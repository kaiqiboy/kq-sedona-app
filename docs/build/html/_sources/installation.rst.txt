Installation
^^^^^^^^^^^^^^^

Download the source code of ST4ML from our `Github repo`__.

 __ https://github.com/kaiqiboy/ST4ML-public

Dependencies
---------------
ST4ML works on a Spark cluster. It is tested with the following dependencies and versions:

* ``Java 1.8.0``
* ``Scala >= 2.12``
* ``Spark >= 3.0``

Data Sources
------------

ST4ML can take data from any Spark-compatable source, e.g.:

* HDFS_
* ElasticSearch_
* `Amazon S3`__

 .. _HDFS: https://hadoop.apache.org/docs/r1.2.1/hdfs_design.html
 .. _ElasticSearch: https://www.elastic.co/elasticsearch/
 __  https://aws.amazon.com/s3/ 
  
The individual dependencies (e.g. Hadoop for HDFS) have to be installed accordingly.
ST4ML uses HDFS as its default data source (to enable optimizations like on-disk data partitioning).
ST4ML is tested with ``Hadoop >= 2.7``.

Compiling and Testing ST4ML
---------------------------

After the source code of ST4ML is downloaded, it can be built using ``sbt`` by ::
   
    sbt assembly

Note: the scala version in ``build.sbt`` should be modified accordingly.

The compiled ``jar`` locates at ``PATH_TO_ST4ML/target/scala-2.xx/st4ml-xxx.jar``.

The ``jar`` can be passed to ``spark-shell`` or ``spark-submit`` commands to enrich Spark with ST processing abilities.
For example:

.. code-block:: shell
    
    spark-shell --jars PATH_TO_ST4ML.jar

Inside ``spark-shell`` console, try the following code: 

.. code-block:: scala

    import st4ml.instances._
    import st4ml.GeometryImplicits._ // test if ST4ML classes can be imported
    val event = Event(Point(0, 0), Duration(0, 1)) // instantiate an ST event with location (0, 0) and temporal duration (0, 1)
    val event2 = event1.mapSpatial(x => x + (1, 2)) // shift the location of the event by 1 and 2 on x and y axis respectively   


You should see the following output: 

.. code-block:: scala

    event2: instances.Event[instances.Point,None.type,None.type] = Event(entries=Array(Entry(POINT (1 2),Duration(0, 1),None)), data=None)






