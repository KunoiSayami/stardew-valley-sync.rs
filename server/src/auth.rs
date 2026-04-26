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
pub struct PinAuthLayer {
    pin: Arc<String>,
}

impl PinAuthLayer {
    pub fn new(pin: impl Into<String>) -> Self {
        Self {
            pin: Arc::new(pin.into()),
        }
    }
}

impl<S> Layer<S> for PinAuthLayer {
    type Service = PinAuthService<S>;

    fn layer(&self, inner: S) -> Self::Service {
        PinAuthService {
            inner,
            pin: self.pin.clone(),
        }
    }
}

#[derive(Clone)]
pub struct PinAuthService<S> {
    inner: S,
    pin: Arc<String>,
}

impl<S> Service<Request<Body>> for PinAuthService<S>
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
        let pin = self.pin.clone();
        let provided = req
            .headers()
            .get("x-sync-pin")
            .and_then(|v| v.to_str().ok())
            .map(|s| s.as_bytes().to_vec())
            .unwrap_or_default();

        let pin_bytes = pin.as_bytes().to_vec();
        let authorized = provided.len() == pin_bytes.len() && provided.ct_eq(&pin_bytes).into();

        if !authorized {
            let body = serde_json::to_string(&ApiErrorResponse {
                error: "unauthorized".to_string(),
                detail: Some("Missing or incorrect X-Sync-Pin header".to_string()),
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
