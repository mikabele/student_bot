package repository.impl.doobie.logger

import doobie.util.log._
import org.apache.logging.log4j._

object logger {

  implicit val log4jLogger: LogHandler = {
    val log4jLogger = LogManager.getLogger("database_layer")
    LogHandler {

      case Success(s, a, e1, e2) =>
        log4jLogger.info(s"""Successful Statement Execution:
                            |
                            |  ${s.split("\n").dropWhile(_.trim.isEmpty).mkString("\n  ")}
                            |
                            | arguments = [${a.mkString(", ")}]
                            |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (${(e1 + e2).toMillis} ms total)
      """.stripMargin)

      case ProcessingFailure(s, a, e1, e2, t) =>
        log4jLogger.error(s"""Failed Resultset Processing:
                             |
                             |  ${s.split("\n").dropWhile(_.trim.isEmpty).mkString("\n  ")}
                             |
                             | arguments = [${a.mkString(", ")}]
                             |   elapsed = ${e1.toMillis} ms exec + ${e2.toMillis} ms processing (failed) (${(e1 + e2).toMillis} ms total)
                             |   failure = ${t.getMessage}
      """.stripMargin)

      case ExecFailure(s, a, e1, t) =>
        log4jLogger.error(s"""Failed Statement Execution:
                             |
                             |  ${s.split("\n").dropWhile(_.trim.isEmpty).mkString("\n  ")}
                             |
                             | arguments = [${a.mkString(", ")}]
                             |   elapsed = ${e1.toMillis} ms exec (failed)
                             |   failure = ${t.getMessage}
      """.stripMargin)

    }
  }
}
