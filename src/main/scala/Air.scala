import org.apache.sedona.core.enums.IndexType
import org.apache.sedona.core.serde.SedonaKryoRegistrator
import org.apache.sedona.core.spatialOperator.RangeQuery
import org.apache.sedona.sql.utils.{Adapter, SedonaSQLRegistrator}
import org.apache.spark.serializer.KryoSerializer
import org.locationtech.jts.geom.{Coordinate, Envelope, GeometryFactory}

import java.lang.System.nanoTime
import org.apache.spark.sql.SparkSession

import scala.io.Source

object Air {
  case class AirRaw(station_id: Int, PM25_Concentration: Double, PM10_Concentration: Double, NO2_Concentration: Double,
                    CO_Concentration: Double, O3_Concentration: Double, SO2_Concentration: Double, t: Long, longitude: Double, latitude: Double)

  case class AirSedona(point: String, station_id: Int, PM25_Concentration: Double, PM10_Concentration: Double, NO2_Concentration: Double,
                       CO_Concentration: Double, O3_Concentration: Double, SO2_Concentration: Double, t: Long)

  def main(args: Array[String]): Unit = {
    val t = nanoTime()
    val dataDir = args(0)
    val mapDir = args(1)
    val queryFile = args(2)
    val numPartitions = args(3).toInt
    val spark = SparkSession.builder()
      .appName("GeoSparkAir")
      .config("spark.serializer", classOf[KryoSerializer].getName)
      .config("spark.kryo.registrator", classOf[SedonaKryoRegistrator].getName)
      .getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")
    SedonaSQLRegistrator.registerAll(spark)
    val gf = new GeometryFactory()
    import spark.implicits._
    val f = Source.fromFile(queryFile)
    val ranges = f.getLines().toArray.map(line => {
      val r = line.split(" ")
      Array(r(0).toDouble, r(1).toDouble, r(2).toDouble, r(3).toDouble, r(4).toLong, r(5).toLong)
    })
    for (range <- ranges) {
      val aqDf = spark.read.parquet(dataDir).as[AirRaw].map(x =>
        AirSedona(gf.createPoint(new Coordinate(x.latitude, x.longitude)).toString,
          x.station_id, x.PM25_Concentration, x.PM10_Concentration, x.NO2_Concentration,
          x.CO_Concentration, x.O3_Concentration, x.SO2_Concentration, x.t)).toDF() // the data labeled wrongly, lon and lat should reverse
      aqDf.createOrReplaceTempView("input")
      val query = "select ST_GeomFromWKT(input.point) AS location, station_id, PM25_Concentration," +
        "PM10_Concentration, NO2_Concentration,CO_Concentration,O3_Concentration,SO2_Concentration, t from input"
      val pointDf = spark.sql(query).repartition(numPartitions)
      val pointRDD = Adapter.toSpatialRdd(pointDf, "location")
      pointDf.unpersist()
      pointRDD.analyze()
      pointRDD.buildIndex(IndexType.RTREE, false)
      val sQuery = new Envelope(range(0), range(2), range(1), range(3))
      val ts = Range(range(4).toInt, range(5).toInt, 86400).sliding(2).toArray
      val selectedRDD = RangeQuery.SpatialRangeQuery(pointRDD, sQuery, true, true)
        .rdd.map(x => (x, x.getUserData.asInstanceOf[String].split("\t")(0).toDouble,
        x.getUserData.asInstanceOf[String].split("\t")(1).toDouble,
        x.getUserData.asInstanceOf[String].split("\t")(2).toDouble,
        x.getUserData.asInstanceOf[String].split("\t")(3).toDouble,
        x.getUserData.asInstanceOf[String].split("\t")(4).toDouble,
        x.getUserData.asInstanceOf[String].split("\t")(5).toDouble,
        x.getUserData.asInstanceOf[String].split("\t").last.toLong))
        .filter(x => x._8 >= range(4) && x._8 <= range(5))
      val map = spark.read.option("delimiter", " ").csv(mapDir).rdd
      val mapRDD = map.map(x => {
        val lsString = x.getString(1)
        val points = lsString.drop(1).dropRight(1).split("\\),").map { x =>
          val p = x.replace("(", "").replace(")", "").split(",").map(_.toDouble)
          new Coordinate(p(0), p(1))
        }
        gf.createLineString(points)
      }).filter(x => x.intersects(gf.toGeometry(sQuery)))
      val maps = for (i <- mapRDD.collect; j <- ts) yield (i, j.toArray)
      map.unpersist()
      mapRDD.unpersist()

      def add(a: Array[Double], b: Array[Double]): Array[Double] = a.zip(b).map { case (x, y) => x + y }

      def add2(a: Array[Array[Double]], b: Array[Array[Double]]): Array[Array[Double]] = a.zip(b).map { case (x, y) => add(x, y) }

      pointRDD.rawSpatialRDD.unpersist()
      if (maps.length > 0) {
        val res = selectedRDD.map(x => maps.map { m =>
          if (m._1.intersects(x._1) && m._2(0) <= x._8 && m._2(1) >= x._8) Array(x._2, x._3, x._4, x._5, x._6, x._7)
          else Array(0.0, 0, 0, 0, 0, 0)
        })
        val a = Array.ofDim[Double](res.take(1).head.length, 6)
        val r = res.aggregate(a)(add2, add2)
        println(r.length)
      }
      else println(0)
    }
    println(s"Grid hourly aggregation ${(nanoTime - t) * 1e-9} s")
    sc.stop()
  }
}
