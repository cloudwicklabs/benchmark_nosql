package com.cloudwick.mongo.inserts

import java.util.concurrent.atomic.AtomicLong
import com.cloudwick.mongo.dao.LogDAO
import org.slf4j.LoggerFactory
import com.mongodb.casbah.Imports._
import com.cloudwick.mongo.OptionsConfig
import com.cloudwick.generator.utils.Utils
import com.cloudwick.generator.log.{LogGenerator, IPGenerator}

/**
 * Inserts events into mongo collection
 * @param eventsStartRange start range of the number of documents to insert
 * @param eventsEndRange end range of the number of documents to insert
 * @param counter atomic counter for keeping track of all threads insert operations
 * @param config scopt parsed command line options
 * @param mongo log event documents data access object
 * @author ashrith 
 */
class Insert(eventsStartRange: Int,
             eventsEndRange: Int,
             counter: AtomicLong,
             config: OptionsConfig,
             mongo: LogDAO) extends Runnable {
  lazy val logger = LoggerFactory.getLogger(getClass)
  lazy val retryBlock = new com.cloudwick.generator.utils.Retry[Unit](config.operationRetires)

  val utils = new Utils
  val ipGenerator = new IPGenerator(config.ipSessionCount, config.ipSessionLength)
  val logEventGen = new LogGenerator(ipGenerator)
  // val sleepTime = if(config.eventsPerSec == 0) 0 else 1000/config.eventsPerSec

  val writeConcern = config.writeConcern match {
    case "none" => WriteConcern.None
    case "safe" => WriteConcern.Safe
    case "majority" => WriteConcern.Majority
  }

  def threadName = Thread.currentThread().getName

  def run() = {
    import retryBlock.retry
    val mongoClient = mongo.initialize
    val collection = mongo.initCollection(mongoClient, config.mongoDbName, config.mongoCollectionName)
    val totalDocs = eventsEndRange - eventsStartRange + 1
    try {
      (eventsStartRange to eventsEndRange).foreach { docCount =>
        retry {
          mongo.addDocument(collection,
            mongo.makeMongoObject(logEventGen.eventGenerate, docCount),
            writeConcern)
        } giveup {
          case e: Exception =>
            logger.debug("failed inserting to document to mongo collection after {} tries, reason: {}",
              config.operationRetires, e.printStackTrace())
        }
        counter.getAndIncrement
      }
      logger.debug(s"Documents inserted by $threadName is: $totalDocs from ($eventsStartRange) to " +
        s"($eventsEndRange)")
      // counter.getAndAdd(totalDocs)
    } finally {
      mongo.close(mongoClient)
    }
  }
}
