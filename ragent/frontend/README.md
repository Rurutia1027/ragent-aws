# Frontend

The web client. It renders the chat, the knowledge-base screens, and the streamed answer. It calls the API over HTTP and does not embed the JVM.

Build and release it as its own image. A frontend change does not roll the API, the tool service, or the queue consumers.
