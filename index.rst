.. ST-Tool documentation master file, created by
   sphinx-quickstart on Tue Oct 13 20:45:28 2020.
   You can adapt this file completely to your liking, but it should at least
   contain the root `toctree` directive.


Welcome to ST4ML's documentation!
=======================================
ST4ML is an ML-oriented distributed spatio-temporal (ST) data processing system.

ST4ML employs a three-stage pipeline "Selection-Conversion-Extraction" and designs five ST instances, where most ST feature extraction applications can fit.

ST4ML works on top of `Apache Spark`__, providing in-memory distributed ST processing ability over Spark's RDD API. 

.. image:: images/overview.PNG
  :width: 600
  :alt: ST4ML overview

__ https://spark.apache.org/

.. toctree::
   :maxdepth: 5

   installation
   gettingstarted
   end2endexample


Indices and tables
==================

* :ref:`genindex`
* :ref:`search`
  
