ElasticSearch Interface
^^^^^^^^^^^^^^^^^^^^^^^

We provided a package to query data from ElasticSearch(ES) and store them into HDFS.

Compilation
===========

In package ``EsInterface``, run 

.. code-block:: bash

        bash sbt assembly


The ``.jar`` file can be found at ``EsInterface/target/scala-2.12/EsInterface-assembly-0.1.jar``.

Read Face Data
===============

Our functions support read data of the following format:


    ==================  ====== ======  ==========
    zjhm                 dqwd   dqjd      gxsj
    ==================  ====== ======  ==========
    340321198106224356  30.28  120.21  1617587125
    340321198106224356  30.29  120.21  1617697127
    ==================  ====== ======  ==========


When querying in ES, it should look like:

.. code-block:: bash


    {
    "_index" : "faces",
    "_type" : "_doc",
    "_id" : "dE6A6XkB-DNVtEy-uYhN",
    "_score" : 1.0,
    "_source" : {
      "zjhm" : "52011120201203",
      "dqwd" : "30.28",
      "dqjd" : "120.23",
      "gxsj" : "1617585125"
    }
  



**Note**:
    + The header names have to be the same as the example above.
    + The table must have the four columns. It is okay that the source have more columns but they are not read.


To query data in ES and save them to HDFS, run 

.. code-block:: bash

    bash EsInterface/scripts/faceEs2Hdfs.sh

which looks like

.. code-block:: bash
  
      spark-submit \
      --class ReadFaceEsDemo\
      --master spark\
      --total-executor-cores 96\ # change according to your environment
      --executor-memory 64g\ # change according to your environment
      --executor-cores 16\ # change according to your environment
      --driver-memory 50g\ # change according to your environment
      /home/kaiqi.liu/EsInterface/target/scala-2.12/EsInterface-assembly-0.1.jar  spark://11.167.227.34:7077  "11.167.227.34" "9200" hz_face "?q=*:*" /datasets/hz_face "256" "/datasets/camerasite.csv"
  
  

Explanation of input arguments:

1. spark master
2. es node
3. es port
4. es index
5. es query to select relevant data (the example takes all data, which takes long)
6. output directory in HDFS 
7. number of partitions to save in HDFS

The reading and saving takes up to hours. Once it is done, the data can be read by 
``ReadFaceJson`` in ``st-tool-app``.


Read Vehicle Data
=================

Our functions support read data of the following format:

    =========  ===================  ===================  ===========  ==================== ========== ========== ==========
    vehicleId  timestamp1           timestamp2           vehicleType  cameraId             attribute1 attribute2 attribute3
    =========  ===================  ===================  ===========  ==================== ========== ========== ==========
    浙D052DQ   2020-08-01 04:42:32  2020-08-01 04:42:32  Car          33010900001322921265 3           2          3
    浙DR07R7   2020-08-01 04:42:32  2020-08-01 04:42:35  Car          33010900001320130282 3           1          1
    =========  ===================  ===================  ===========  ==================== ========== ========== ==========


When querying in ES, it should look like:

.. code-block:: bash

    {
    "_index" : "vhc",
    "_type" : "_doc",
    "_id" : "6lJB5XkBNlHTSfA1IrHM",
    "_score" : 1.0,
    "_source" : {
      "vehicleId" : "µ┤₧CDH199K",
      "timestamp1" : 1596228153,
      "timestamp2" : 1596228188,
      "vehicleType" : "Car",
      "cameraId" : "33010900001322920402",
      "attribute1" : 3,
      "attribute2" : 2,
      "attribute3" : 2
    }
  }


**Note**:
  + The table must have the three columns: ``vehicleId``, ``timeStamp1``, ``cameraId``. Others are optional.


To query data in ES and save them to HDFS, run 

.. code-block:: bash

  bash EsInterface/scripts/vhcEs2Hdfs.sh

which looks like

.. code-block:: bash
  
      spark-submit \
      --class ReadVhcEsDemo\
      --master spark\
      --total-executor-cores 96\ # change according to your environment
      --executor-memory 64g\ # change according to your environment
      --executor-cores 16\ # change according to your environment
      --driver-memory 50g\ # change according to your environment
      /home/kaiqi.liu/EsInterface/target/scala-2.12/EsInterface-assembly-0.1.jar  spark://11.167.227.34:7077  "11.167.227.34" "9200" vhc "?q=*:*" /datasets/xs_vhc "256" "/datasets/camerasite.csv"
  
  

Explanation of input arguments:

1. spark master
2. es node
3. es port
4. es index
5. es query to select relevant data (the example takes all data, which takes long)
6. output directory in HDFS 
7. number of partitions to save in HDFS
8. path to ``camerasite.csv``


The function will read in all vehicle records and group them according to their vehicle ID.
The results in HDFS are stored as trajectories.



The reading and saving takes up to hours. Once it is done, the data can be read by 
``ReadVhc`` in ``st-tool-app``