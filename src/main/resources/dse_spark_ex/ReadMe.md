This document explains on how to work with [Spark in DSE](http://www.datastax.com/documentation/datastax_enterprise/4.5/datastax_enterprise/spark/sparkTOC.html) using the data generated by benchmark.

Make sure you have ran the benchmark to generate & insert some data into cassandra. And also make sure that dse-spark cluster is running.

### Simple dse-spark examples

Launch spark shell

    dse spark

Once you shell has been launched make sure you can see the keyspace's that you are working on using:

    scala> :showSchema

Load data from cassandra to spark using (make sure you only load columns that you need):

    scala> case class MoviesGenre(releaseYear: Int, movieName: String)
    scala> val mgrdd = sc.cassandraTable[MoviesGenre]("moviedata", "movies_genre").select("release_year", "movie_name")

using, this cassandra data is mapped into scala objects and exposed as `CassandraRDD`

Now, query only for 'history' genre:

    scala> val histrdd = mgrdd.where("genre = ?", "history")

Iterate and print:

    scala> histrdd.toArray.foreach(println)

Sort by year:

    scala> histrdd.sortBy(_.releaseYear).reverse.toArray.foreach(println)

### Performing JOINS using dse-spark

Create a table to store the output

    CREATE TABLE watch_history_genre (
      cid INT,
      mid INT,
      movie_name TEXT,
      movie_genre TEXT,
      PRIMARY KEY ((cid), mid)
    );

The following commands will perform JOIN on movies & history data sets:

```scala
case class MovieGenre(genre: String, releaseYear: Int, mid: Int, duration: Int, movieName: String)
case class WatchHistory(cid: Int, mid: Int, movieName: String, pt: Int, ts: java.util.Date)
case class WatchHistoryGenre(cid: Int, mid: Int, movieName: String, movieGenre: String)

val movies = sc.cassandraTable[MovieGenre]("moviedata", "movies_genre").cache
val history = sc.cassandraTable[WatchHistory]("moviedata", "watch_history").cache

// Create a keyed map of [mid, movies] and [mid, history]
val moviesByMid = movies.keyBy(_.mid)
val historyByMid = history.keyBy(_.mid)

// Join the tables by mid
val joined = historyByMid.join(moviesByMid).cache

// Create RDD with a new object type which maps to our new table
val watchHistoryGenre = joined.map({ case (key, (movie, history)) => new WatchHistoryGenre(history.cid, movie.mid, movie.movieName, movie.genre) }).cache

val newRdd = sc.parallelize(watchHistoryGenre)

newRdd.saveToCassandra("moviedata", "watch_history_genre")
```