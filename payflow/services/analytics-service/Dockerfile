FROM python:3.12-slim AS build
WORKDIR /app
COPY pyproject.toml README.md ./
COPY app app
RUN pip install --no-cache-dir --prefix=/install .

FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY --from=build /install /usr/local
COPY app app
COPY inbox inbox
RUN addgroup --system paynexus && adduser --system --ingroup paynexus paynexus \
    && chown -R paynexus:paynexus /app
USER paynexus
EXPOSE 8087
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=10 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8087/health', timeout=3)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8087"]
