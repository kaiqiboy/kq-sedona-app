import org.apache.sedona.core.serde.SedonaKryoRegistrator
import org.apache.sedona.sql.utils.SedonaSQLRegistrator
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.sql.SparkSession

import java.lang.System.nanoTime
import scala.io.Source

object Osm {
  def main(args: Array[String]): Unit = {
    val t = nanoTime()
    val dataDir = args(0)
    val mapDir = args(1)
    val queryFile = args(2)
    val spark = SparkSession.builder()
      .appName("GeoSparkOsm")
      .config("spark.serializer", classOf[KryoSerializer].getName)
      .config("spark.kryo.registrator", classOf[SedonaKryoRegistrator].getName)
      .getOrCreate()
    val sc = spark.sparkContext
    sc.setLogLevel("ERROR")
    SedonaSQLRegistrator.registerAll(spark)
    val f = Source.fromFile(queryFile)
    val ranges = f.getLines().toArray.map(line => {
      val r = line.split(" ")
      Array(r(0).toDouble, r(1).toDouble, r(2).toDouble, r(3).toDouble)
    })
    for (range <- ranges) {
      val rawDf = spark.read.json(dataDir)
      rawDf.createOrReplaceTempView("input")
      val query1 = "select ST_GeomFromWKT(input.g) as location, " +
        "id, timestamp, changeSetId, uid, uname, tagsMap from input"
      val pointDf = spark.sql(query1)
      pointDf.createOrReplaceTempView("input2")
      val query2 = s"select * from input2 where " +
        s"ST_Contains(ST_PolygonFromEnvelope(${range(0)}, ${range(2)}, ${range(1)}, ${range(3)}), location)"
      val selectedPoiDf = spark.sql(query2)
      selectedPoiDf.createOrReplaceTempView("poi")

      val rawDf2 = spark.read.json(mapDir)
      rawDf2.createOrReplaceTempView("postal")
      val query3 = "select ST_GeomFromWKT(postal.g) as area, " +
        "id, version, timestamp, changeSetId, uid, uname, tagsMap from postal"
      val postalDf = spark.sql(query3)
      postalDf.createOrReplaceTempView("input3")
      val query4 = s"select * from input3 where " +
        s"ST_Contains(ST_PolygonFromEnvelope(${range(0)}, ${range(2)}, ${range(1)}, ${range(3)}), area)"
      val selectedAreaDf = spark.sql(query4)
      selectedAreaDf.createOrReplaceTempView("area")
      val query5 = "select * from poi, area where ST_Intersects(ST_makeValid(poi.location), ST_makeValid(area.area))"
      val res = spark.sql(query5)
      res.createOrReplaceTempView("res")
      val query6 = "select count(*) from res group by area"
      val grouped = spark.sql(query6)
      grouped.write.format("noop")
      println(res.count)
    }
    println(s"osm aggregation ${(nanoTime - t) * 1e-9} s")
    sc.stop()
  }
}
