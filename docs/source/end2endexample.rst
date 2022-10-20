End-to-End Example
^^^^^^^^^^^^^^^^^^^

In this section, we present an end-to-end example to show how an ST-related ML features can be extracted using ST4ML.


Suppose we want to build a deep learning model to predict the future traffic condition (flow, speed, and etc.).
To train such a model, we need to know the historical traffic conditions (per area and at different times). This information is often
not directly available, instead we have enormous raw trajectory data.

Therefore, the traffic condition (we use speed as an example) extraction problem can be formulated as:
given trajectories and a raster structure, find the average speed of each raster cell.
Suppose we divide a city in to a 10*10 grid, and are interested in hourly speed per grid cell.

First, the raster can be read from a file with specific format (line 1 below) or constructed an empty raster as elaborated :ref:`here <raster>`. 

Next, the programmer initiates three operators (lines 4-6) and call the execution functions in sequence (lines 8-10). In this example, a built-in extractor is used,
the programmer may also implement their customized extractors. 

Last, the result can be saved via the saving function (line 12). The result from line 10 is an RDD, the programmer is also
free to reformatted as saved as in a format that is preferred by deep learning platforms (e.g., csv).


.. code-block:: scala
  :linenos:

    // read raster structure
    val raster =  ReadRaster(rasterFile)
    // initialize operators
    val selector = Selector[STTraj](sQuery, tQuery)
    val converter = Traj2RasterConverter(raster)
    val extractor = RasterSpeedExtractor()
    // execute the application
    val trajRDD = selector.select(dataDir, metadataDir)
    val rasterRDD = converter.convert(trajRDD)
    val speedRDD = extractor.extract(rasterRDD)
    // save results
    SaveParquet(speedRDD)


