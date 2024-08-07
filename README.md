# Prometheus exporter for EcoFlow products

Exports EcoFlow mqtt messages to prometheus. Based on 
[berezhinskiy](https://github.com/berezhinskiy/ecoflow_exporter/) 
exporter.

## Usage

Parameters:
- `ECOFLOW_USERNAME` - EcoFlow account login
- `ECOFLOW_PASSWORD` - EcoFlow account password
- `ECOFLOW_DEVICES` - coma separated list of pairs: 
device serial number (RXXXXXXXXXXXXXXX) and 
arbitrary device name (used in device tag) separated with colon.  
Example: `ECOFLOW_DEVICES="R111222333444555:Delta 2 Max,R555444333222111:River 2 Pro"`

```bash
docker run -p 8080:8080 -it \
-e ECOFLOW_USERNAME=<ecoflow account username> \
-e ECOFLOW_PASSWORD=<ecoflow account password> \
-e ECOFLOW_DEVICES="<device serial number>:<device name>,<device serial number>:<device name>" \
-v /path/on/host:/data \
--name ecoflow_exporter \
ghcr.io/429076/ecoflow_exporter
```

Volume `/data` is used to persist credentials and prevent unnecessary login attempts during restart.