# Leave the Brokers Outside the Cluster

## Deploying a knowledge Q&A system on AWS, and trading RocketMQ for Kafka

The system answers questions from a private document set. People upload files into a knowledge base, ask in natural language, and get a streamed answer with the passages it used. They can mark an answer up or down. Indexing a file, deleting a knowledge base, and saving that vote are too slow, or too unimportant, to finish inside the chat request. Those three jobs are handed to a message queue. Everything else — login, the live answer, the fair queue in front of the model — does not go through it.

We are putting this system on AWS. The API, the tool service, and the web assets run on EKS. Postgres, Redis, and object storage move to RDS, ElastiCache, and S3. The queue is the piece that does not have a single obvious managed replacement, because today it is RocketMQ.

## The tradeoff: keep RocketMQ, take SQS, or run Kafka

RocketMQ is a good fit for the code as it stands. Two of the three jobs are published as transaction messages. The broker hides the record until the local database transaction commits, and if the process dies in between, the broker calls back and checks the row. On one machine that promise is easy to believe: the broker, the database, and the only application process live together.

Taking that broker onto EKS is the option we dropped first. A broker needs disk, a controller quorum, and more than one availability zone. A dead API pod is replaced and takes traffic again. A full broker disk stops indexing, cleanup, and feedback together. We already decided not to operate Postgres or Redis inside the cluster. The queue belongs to the same rule.

SQS is the short path on AWS, and we weighed it. There is no broker to patch, and a standard queue is enough for a job that one worker consumes and then forgets. Two costs come with that simplicity. The record is deleted once it is acknowledged, so a failed indexing job cannot be replayed from the original event. A second reader, for audit or for metrics, cannot subscribe beside the worker without an SNS topic in front. And SQS has no half-message and no callback into Postgres, so the "publish only after commit" promise still has to be rebuilt in an outbox table. The application code does not get smaller. What disappears is the log.

Kafka keeps the log. A topic can have one worker group now and a metrics group later. Records stay for a retention window, so a bad run can be read again. Partition keys keep one document's indexing commands in order. The price is the client model: partitions, consumer groups, offsets, and manual commit. Kafka transactions do not replace the RocketMQ callback either. They make several records succeed or fail together. They do not ask Postgres whether the row was written. That promise still becomes an outbox. We pay that cost once, and we get a log we can subscribe to more than once.

The brokers themselves we do not operate. Amazon MSK runs them: patching, failure detection, and a spread across zones. We choose the size. We do not shell in and edit server config. EKS runs the producers and the consumers. Our code still owns offsets, retries, idempotency, and the alarms on the seam between RDS and the log. MSK maintains the processes and the disks. We maintain delivery. That split is the hybrid.

| State | Where it lives |
|:---|:---|
| Q&A API, tool server, web | EKS |
| Business rows and vectors | RDS for PostgreSQL |
| Sessions, fair queue, locks | ElastiCache for Redis |
| Documents and images | S3 |
| The three background jobs | MSK |

MSK and the worker nodes share a VPC and private subnets. Three zones, three brokers, replication factor 3, minimum in-sync replicas 2. Clients use IAM on port 9098, and pods assume a role. Producers use `acks=all`. Consumers commit only after the handler succeeds.

## What comes next: the queue is used in three places

We counted the call sites. The whole service depends on the message queue in three places, and nowhere else. Each one becomes a topic. Retention and retry stay different on purpose, so they do not collapse into a single topic with a header switch.

**Document indexing.** A person starts indexing an uploaded file. In the same database transaction the file is marked running, and a record is queued. The consumer extracts the text, splits it, and writes the vectors. The topic is there so this work does not sit inside the HTTP request. The record key is the document id, so a second command for the same file stays on the same partition. If the process dies after the commit and before the send, the outbox row is still in RDS and the send is retried. The consumer treats a repeat as safe: a document already indexed is not indexed twice from scratch.

**Knowledge-base cleanup.** A knowledge base can be removed only after its documents are gone. The delete soft-removes the base, and only then is a cleanup record published. The consumer drops the vectors, the stored files, and any keyword or graph index that belonged to it. The topic is there so the delete click does not wait on that physical work. The key is the knowledge-base id. If vectors, files, or an index fail, the whole record is retried, and each step can run twice.

**Answer feedback.** A like or a dislike is written off the request path. Losing one is annoying, not fatal. The user can click again. This one is a plain send, with no outbox. It rides the same client as the other two so the service does not operate two queue systems for a single fire-and-forget call.

Chat, retrieval, and the model call never publish to these topics. A replica count on the API does not, by itself, change how the three jobs are consumed. Every pod joins the same consumer group per topic.

## Watching the half we maintain

MSK will not say that a file has been "running" for an hour, or that an outbox row was committed and never sent. Broker health is the managed half. The seam is ours. Four alarms cover it.

The application publishes the age of the oldest unsent outbox row. A few minutes means the database commit happened and the produce to MSK did not.

MSK already exports consumer-group lag to CloudWatch, as `SumOffsetLag` and `EstimatedMaxTimeLag`, per topic. Lag climbing while API CPU is idle means indexing is slow. Scale the consumers against lag. Leave API autoscaling on CPU and on the Redis queue depth.

Time spent in "running" is the business gauge. A record can be committed and the handler can still be stuck on a model call or a vector write. Lag is the queue. This number is the job.

Broker vital signs stay on the MSK metrics that already exist. `UnderReplicatedPartitions` and `OfflinePartitionsCount` mean the log is short a replica. `ActiveControllerCount` other than one means the cluster has not finished electing. Those page for the cluster. Outbox age and running-document age page for the application.

A handler that keeps failing lands on a dead-letter topic, one per original topic. Depth there is a poison message, not a capacity problem. The dashboard is two rows: lag, outbox age, sends, and handler failures for the three topics; under-replicated partitions, offline partitions, and produce latency for the brokers.

The port itself is the three producers and the three consumers, plus the outbox for the two jobs that must follow a database commit. The chat path stays where it is.
