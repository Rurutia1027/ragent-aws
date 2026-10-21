# Backend

The Q&A API, the tool service, retrieval, and the clients for the three topics.

`document-indexing`, `knowledge-base-cleanup`, and `answer-feedback` are produced and consumed here. The web client is not. Broker install is not. Those stay in `frontend/` and `deploy/`.

Ship this tree as its own image so an API or consumer change can roll without rebuilding the web client.
