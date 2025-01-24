# Prometheus exporter for EcoFlow products

Exports EcoFlow mqtt messages to prometheus.

## Usage

Parameters:
- `ECOFLOW_ACCESS_KEY` - EcoFlow developer access key
- `ECOFLOW_SECRET` - EcoFlow developer secret

```bash
docker run -p 8080:8080 -it \
-e ECOFLOW_API_ZONE_ID=<timezone for timestamp used in ecoflow API signature: UTC+5> \
-e ECOFLOW_ACCESS_KEY=<ecoflow developer access key> \
-e ECOFLOW_SECRET=<ecoflow developer secret> \
--name ecoflow_exporter \
ghcr.io/429076/ecoflow_exporter
```
