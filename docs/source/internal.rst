ST-Tool Internals
^^^^^^^^^^^^^^^^^

In this chapter, we go deep into ST-Tool and touch some of the internal design idea and key techniques. After reading this chapter, one should be able to develop ST-related applications with high performance flexibly.

The Three Steps
---------------

We abstract all ST-related applications into a pipeline of three steps, namely selection, conversion and extraction.

Selection
===========

Once we have a huge ST dataset, we need to firstly select the portion of data that is related to our application. More specifically, 
with a given spatial(rectangle) and temporal range, we select all data inside it. ST-Tool applies a spatial range query followed by a tempral range query.


Spatial Query
::::::::::::::

The spatial query consists of three sub-steps: *partitioning*, *indexing*, and *selecting*.

Partitioning:
'''''''''''''

ST-Tool has three build-in partitioning methods:

1) Hash partitioner:
   
   The hash partitioner ensures load balance but not data locality. It is the default partitioner used in this step.

2) STR partitioner (`details <https://apps.dtic.mil/dtic/tr/fulltext/u2/a324493.pdf>`__): 

   STR partitioner ensures both load balance and data locality, which can be useful when the following operations can benefit from it (e.g. the selectivity is small or multiple selections are performed concurrently).
   
   However, calculating STR boundaries takes some time as a trade off.

3) QuadTree partitioner (`details <https://en.wikipedia.org/wiki/Quadtree>`__):

   QuadTree lies in between of hash and STR, which achieves some load balance and some data locality. Generating the boundaries is faster than STR.

STR and QuadTree partitioner takes a ``samplingRate`` parameter (ranges from 0 to 1), which means the ratio of data to be used for calculating the boundaries. A higher sampling rate ensures a better load balance but take more time.

Indexing and Selecting:
''''''''''''''''''''''''

Currently ST-Tool uses RTree index inside each partition to query objects. From our experience it has a fairly good performance in terms of both index generation and querying.

Temporal Query
:::::::::::::::

After we filtered out spatially irrelevant data, we do the same for temporal dimension by simply apply a element-wise filter.

The ``TemporalPartitioner`` has two more functions which partition temporally first followed by spatially, namely ``partitionGrid`` and ``PartitionSTR``. If needed, please refer to its API documentations.


Conversion
==========

The second step is conversion, which allow different instances to convert to each other. This is useful since the instance from the input data may not be the most suitable form for the specific application.

For examples, we may want to convert a trajectory to a set of points if our application is to find the number of occurrence of vehicles in a time window at a cetain spot.
More detailed explanations can be found at `ST Instances and Conversions`_.


Extraction
===========

Extraction operates on the converted RDD and performs the actual data analytic algorithms. ST-Tool has some build-in extractors and users can also write their own extraction functions following the guide at `Extensions and Customizations`_.


ST Instances and Conversions
-----------------------------

ST-Tool supports 5 most common instances: point, trajectory, time series, spatial map and raster.
They can convert to each other in order to achieve a better performance in the extraction of different features. 

Firstly let's take a look at the 5 instances.

Point and Trajectory Instances
==============================


Normally the data read in are points or Trajectories, which are defined as following:

.. code-block:: scala    

    case class Point(

        coordinates: Array[Double],
        var t: Long = 0, 
        var id: String = "0", 
        attributes: Map[String, String] = Map()
        
        ) 


.. code-block:: scala

    case class Trajectory(

        tripID: String,
        startTime: Long,
        points: Array[Point],
        attributes: Map[String, String] = Map()
        
        )

Note: 

    1. The timestamp is in unix timestamp and in second.
   
    2. The conventional coordinates are in (longitude, latitude) order.


The start point of ST-Tool are data in ``RDD[Point]`` or ``RDD[Trajectory]``, which are usually read from some files and converted to RDD using ``sc.parallelize(SomeArray)``.

Spatial Map Instances
=====================

A spatial map records the information of a collection of regions at a certain time window, and is defined as 

.. code-block:: scala

    case class SpatialMap[T: ClassTag](

        id: String, 
        timeStamp: (Long, Long), 
        contents: Array[(Rectangle, Array[T])]
        
        )
    
Time Series Instances
=====================

A time series records the information at some location (or region) for a period of time, and is defined as 

.. code-block:: scala

    case class TimeSeries[T: ClassTag](

        id: String,
        startTime: Long,
        timeInterval: Int,
        spatialRange: Rectangle,
        series: Array[Array[T]])

        )

Time interval means the granularity of the time series, i.e. for how long is the information aggregated.

E.g. 

    | startTime = 0, timeInterval = 5 refers to a time series with slots (0,5), (5,10), (10,15) ...(depends on the content length).


Raster Instances
================

A raster refers to a cube in the spatio-temporal space and is defined as 

.. code-block:: scala
    
    case class Raster[T: ClassTag](

        id: String,
        contents: Array[TimeSeries[T]]

        )

The minimal raster cube should have only one TimeSeries with one slot as content.

*Raster instance is not fully developed yet. Not recommended to use for now.*


Conversions
===========

ST-Tool provides the following conversions:

.. image:: images/instances.png   
    :alt: Instances and conversions

The above conversions have corresponding converter classes as  ``converter.A2BConverter``, where ``A`` and ``B`` can be selected from in ``{Point, Traj, SpatialMap, TimeSeries}``
To instantiate a converter, some input arguments may be needed and please refer to the API document.

ST-Tool also has a ``DoNothingConverter`` for completing the 3-step pipeline.

After **step 1: Selection**, the ``dataRDD: RDD[T]`` becomes ``selectedRDD[(Int, T)]`` where the ``Int`` refers to the partition number. The converter takes ``selectedRDD[(Int, T)]`` and returns a ``convertedRDD[C]``. 
This is done by calling  ``A2BConverter.convert(selectedRDD)``.

Build-In Extractors
-------------------

ST-Tool has some useful build-in extractors which can be called inside your own application. The whole list is as below:
    - FakePlateExtractor

    .. code-block:: scala    

         extract(rdd: RDD[Trajectory], speedThreshold: Double): RDD[String]
         extractAndShowDetail(rdd: RDD[Trajectory], speedThreshold: Double):RDD[(String, Array[((Long, (Double, Double)), (Long, (Double, Double)), Double)])]
  
    - PointCompanionExtractor

    .. code-block:: scala    

        extract(sThreshold: Double, tThreshold: Double)(pRDD: RDD[Point]): RDD[(String, Map[Long, String])]

    - PointAnalysisExtractor
  
    .. code-block:: scala    

        extractMostFrequentPoints(n: Int)(pRDD: RDD[Point]): Array[(String, Int)]
        extractPermanentResidents(t: (Long, Long), occurrenceThreshold: Int)(pRDD: RDD[Point]): Array[(String, Int)]
        extractNewMoveIn(t: Long, occurrenceThresholdAfter: Int, occurrenceThresholdBefore: Int = 3)(pRDD: RDD[Point]): Array[(String, Int)]
        extractSpatialRange(pRDD: RDD[Point]): Array[Double]
        extractTemporalRange(pRDD: RDD[Point]): Array[Long]
        extractTemporalQuantile(percentages: Array[Double])(pRDD: RDD[Point]): Array[Double]
        extractTemporalQuantile(percentages: Double)(pRDD: RDD[Point]): Double
        extractAbnormity(range: (Int, Int) = (23, 5))(pRDD: RDD[Point]): Array[String]
        extractNumIds(pRDD: RDD[Point]): Long
        extractDailyNum(pRDD: RDD[Point]): Array[(String, Int)]
        extractNumAttribute(key: String)(pRDD: RDD[Point]): Long

    - SpatialMapExtractor
    .. code-block:: scala    

        rangeQuery[T <: Shape : ClassTag](rdd: RDD[SpatialMap[T]], spatialRange: Rectangle, temporalRange: (Long, Long)): RDD[T]

    - TimeSeriesExtractor
    .. code-block:: scala    

        extractByTime[T <: Shape : ClassTag](timeRange: (Long, Long))(rdd: RDD[TimeSeries[T]]): RDD[T]
        countTimeSlotSamples[T: ClassTag](timeRange: (Long, Long))(rdd: RDD[TimeSeries[T]]): RDD[((Long, Long), Int)]
        extractByTimeSpatial[T <: Shape : ClassTag](timeRange: (Long, Long))(rdd: RDD[TimeSeries[T]]): RDD[T]
        countTimeSlotSamplesSpatial[T <: Shape : ClassTag](timeRange: (Long, Long))(rdd: RDD[TimeSeries[T]]): RDD[Array[((Long, Long), Int)]]

Extensions and Customizations
-----------------------------

Once we have finished selection and conversion, we can pass the ``convertedRDD`` to the final extraction step. ST-Tool provides useful extraction functions but still cannot cover all.
In complement, ST-Tool supports customized extraction functions. However, for now developers have to be familiar with Spark and write some Spark codes operating RDDs. Basically, what you need to do is to write a function which takes the ``RDD[T]`` from **step 2: Conversion** as input and do whatever you want.
You can print out the results or save it into a file after finishing the process.

Note that the implementation of the algorithm affects the performance.

Below is an example of extracting companion relationship from points:

.. code-block:: scala

    package customExtractor

    import geometry.Point
    import operators.extraction.Extractor
    import operators.selection.partitioner._
    import org.apache.spark.rdd.RDD
    import org.apache.spark.storage.StorageLevel
    import utils.Config

    import scala.math.abs

    class CompanionExtractor extends Extractor {

    def isCompanion(tThreshold: Double, sThreshold: Double)(p1: Point, p2: Point): Boolean = {
    abs(p1.timeStamp._1 - p2.timeStamp._1) <= tThreshold &&
        abs(p1.geoDistance(p2)) <= sThreshold &&
        p1.attributes("tripID") != p2.attributes("tripID")
    }

    // find all companion pairs
    def optimizedExtract(sThreshold: Double, tThreshold: Double, tPartition: Int = 4)
                        (pRDD: RDD[Point]): RDD[(String, Map[Long, String])] = {

    val numPartitions = pRDD.getNumPartitions
    val partitioner = new TemporalPartitioner(startTime = pRDD.map(_.t).min,
        endTime = pRDD.map(_.t).max, numPartitions = numPartitions)
    val repartitionedRDD = partitioner.partitionSTR(pRDD, tPartition, tThreshold, sThreshold, Config.get("samplingRate").toDouble) //temporal + str
    val pointsPerPartition = repartitionedRDD.mapPartitions(iter => Iterator(iter.length)).collect
    println("--- After partitioning:")
    println(s"... Number of points per partition: " +
        s"${pointsPerPartition.deep}")
    println(s"... Total: ${pointsPerPartition.sum}")
    println(s"... Distinct: ${repartitionedRDD.map(x => x._2.id + x._2.t.toString).distinct.count}")
    repartitionedRDD.persist(StorageLevel.MEMORY_AND_DISK_SER)
    repartitionedRDD.join(repartitionedRDD).map(_._2).filter {
        case (p1, p2) => isCompanion(tThreshold, sThreshold)(p1, p2)
    }.map {
        case (p1, p2) => (p1.id, Array((p1.timeStamp._1, p2.id)))
    }
        .mapValues(_.toMap)
        .reduceByKey(_ ++ _, numPartitions * 4)
    }
    }


In addition, ST-Tool supports custom ``Partitioner``, ``Indexer`` and  ``Converter`` as well. If needed, please contact the authors for further details.
