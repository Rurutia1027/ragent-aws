# ragent-aws

English edition of the knowledge Q&A service, deployed on AWS. EKS runs the application. MSK holds the three background topics. RDS, ElastiCache, and S3 hold the rest.

The corpus is English. A Chinese document stored in the knowledge base is retrieved as Chinese, and the answer follows it. Translating the reply prompt is not enough.

## Layout

| Path | What lands here |
|:---|:---|
| `frontend/` | Web client. Own image, own Deployment. |
| `backend/` | Q&A API, tool service, retrieval, and the three queue clients. Own image. |
| `deploy/` | How the two images are installed: Helm, and the AWS bindings for MSK, RDS, and S3. |
| `harness/` | Eval suites, tasks, and the checks that a change did not degrade retrieval or the agent. |
| `knowledge/` | Documents that are stored, chunked, and embedded. Seed data lives here, not in `backend/`. |
| `docs/` | Design notes and the Kafka deployment write-up. |

## What has to be English

- Documents under `knowledge/`, including anything uploaded later into a knowledge base.
- Prompts and the fixed instructions shipped with the service.
- Harness questions, expected evidence, and reference answers.
- Text the product shows to a person.

Code identifiers, topic names, and log keys stay stable across languages. They are not the corpus.
