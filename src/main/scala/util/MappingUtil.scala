package util

import domain.queue._

object MappingUtil {
  object DbDomainMappingUtil {
    def dbReadQueueSeriesToQueueSeries(
      dbQueueSeries: List[QueueSeriesDbReadDomain],
      queues:        List[Queue]
    ): List[QueueSeries] = {
      dbQueueSeries.map(qs =>
        QueueSeries(qs.id, qs.name, qs.university, qs.course, qs.group, queues.filter(_.queueSeriesId == qs.id))
      )
    }
  }
}
