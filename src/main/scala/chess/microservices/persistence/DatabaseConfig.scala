package chess.microservices.persistence

import com.typesafe.config.{Config, ConfigFactory}

/** Configuration for database connections
  *
  * Loads from application.conf and environment variables.
  */
case class DatabaseConfig(
    active: String,
    mongodb: MongoConfig,
    postgres: PostgresConfig
)

case class MongoConfig(
    uri: String,
    database: String
)

case class PostgresConfig(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    poolSize: Int
)

object DatabaseConfig:
  /** Load database configuration from application.conf
    */
  def load(): DatabaseConfig =
    val config = ConfigFactory.load()
    load(config)

  /** Load database configuration from a specific Config object
    */
  def load(config: Config): DatabaseConfig =
    val dbConfig = config.getConfig("database")

    DatabaseConfig(
      active = dbConfig.getString("active"),
      mongodb = MongoConfig(
        uri = dbConfig.getString("mongodb.uri"),
        database = dbConfig.getString("mongodb.database")
      ),
      postgres = PostgresConfig(
        host = dbConfig.getString("postgres.host"),
        port = dbConfig.getInt("postgres.port"),
        database = dbConfig.getString("postgres.database"),
        user = dbConfig.getString("postgres.user"),
        password = dbConfig.getString("postgres.password"),
        poolSize = dbConfig.getInt("postgres.pool-size")
      )
    )
