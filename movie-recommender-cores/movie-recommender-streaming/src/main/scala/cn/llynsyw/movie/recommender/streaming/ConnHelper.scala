package cn.llynsyw.movie.recommender.streaming

import com.mongodb.casbah.{MongoClient, MongoClientURI}
import redis.clients.jedis.Jedis

// 定义连接助手对象，序列化
object ConnHelper extends Serializable {
  lazy val jedis = new Jedis("localhost")
  lazy val mongoClient = MongoClient(MongoClientURI("mongodb://localhost:27017/recommender"))
}
