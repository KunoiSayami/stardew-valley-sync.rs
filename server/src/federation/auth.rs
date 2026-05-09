use std::{
    future::Future,
    pin::Pin,
    sync::Arc,
    task::{Context, Poll},
};

use axum::{
    body::Body,
    http::{Request, Response, StatusCode},
};
use common::ApiErrorResponse;
use subtle::ConstantTimeEq;
use tower::{Layer, Service};

#[derive(Clone)]
pub struct FederationAuthLayer {
    token: Arc<String>,
}

impl FederationAuthLayer {
    pub fn new(token: impl Into<String>) -> Self {
        Self {
            token: Arc::new(token.into()),
        }
    }
}

impl<S> Layer<S> for FederationAuthLayer {
    type Service = FederationAuthService<S>;

    fn layer(&self, inner: S) -> Self::Service {
        FederationAuthService {
            inner,
            token: self.token.clone(),
        }
    }
}

#[derive(Clone)]
pub struct FederationAuthService<S> {
    inner: S,
    token: Arc<String>,
}

impl<S> Service<Request<Body>> for FederationAuthService<S>
where
    S: Service<Request<Body>, Response = Response<Body>> + Clone + Send + 'static,
    S::Future: Send + 'static,
{
    type Response = Response<Body>;
    type Error = S::Error;
    type Future = Pin<Box<dyn Future<Output = Result<Self::Response, Self::Error>> + Send>>;

    fn poll_ready(&mut self, cx: &mut Context<'_>) -> Poll<Result<(), Self::Error>> {
        self.inner.poll_ready(cx)
    }

    fn call(&mut self, req: Request<Body>) -> Self::Future {
        let token = self.token.clone();
        let provided = req
            .headers()
            .get("x-federation-token")
            .and_then(|v| v.to_str().ok())
            .map(|s| s.as_bytes().to_vec())
            .unwrap_or_default();

        let token_bytes = token.as_bytes().to_vec();
        let authorized = provided.len() == token_bytes.len() && provided.ct_eq(&token_bytes).into();

        if !authorized {
            let body = serde_json::to_string(&ApiErrorResponse {
                error: "unauthorized".to_string(),
                detail: Some("Missing or incorrect X-Federation-Token header".to_string()),
            })
            .unwrap_or_default();

            return Box::pin(async move {
                Ok(Response::builder()
                    .status(StatusCode::UNAUTHORIZED)
                    .header("content-type", "application/json")
                    .body(Body::from(body))
                    .unwrap())
            });
        }

        let fut = self.inner.call(req);
        Box::pin(async move { fut.await })
    }
}
