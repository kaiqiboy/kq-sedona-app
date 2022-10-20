.. _data_standard:
Data Standard in ST4ML
^^^^^^^^^^^^^^^^^^^^^^^^

On-Disk Data Standard
======================

Event
-----

The on-disk events should be stored as ``Parquet`` files and contain the following fields:

.. code-block:: scala

    case class E(shape: String, 
                 timeStamp: Array[Long], 
                 v: Option[String], 
                 d: String)

where the shape ``String`` follows the WKT_ format. E.g., ``POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))``

  .. _WKT:  https://en.wikipedia.org/wiki/Well-known_text_representation_of_geometry

Trajectory
----------

The on-disk trajectories are defined as follows:

.. code-block:: scala

    case class TrajPoint(lon: Double, 
                         lat: Double, 
                         t: Array[Long], 
                         v: Option[String])

    case class T(points: Array[TrajPoint], d: String)