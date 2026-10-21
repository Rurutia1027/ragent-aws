# Deploy

Install path for this service.

- Two images and two Deployments: `frontend` and `backend`. They roll separately.
- Bindings to MSK, RDS, ElastiCache, and S3. Brokers are not scheduled as pods.
- Runtime config: bootstrap servers, topic names, and the shared consumer group ids.

The lab that creates a VPC, EKS, and MSK from scratch lives in `Cloud-Native-Microservices-on-AWS/examples/msk-eks-hybrid`. This directory is the application install, not that lab.
