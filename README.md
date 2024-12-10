# Audit Report Tool
=====================

The **Audit Report Tool** is primarily a **CLI tool** that parses Protobuf and Scala files across multiple GitHub repositories, extracting services, RPC methods, REST endpoints, dependencies, and SAML configurations. It generates comprehensive reports (in JSON and CSV formats) that can be used for internal audit and compliance processes.

Optionally, an **SBT plugin** is available as an add-on, but the CLI tool is the main focus for producing the required reports.

---

## Overview

The CLI tool:

1. Clones and/or processes multiple repositories organized by product and project.
2. Extracts gRPC services, REST endpoints, dependencies, and SAML-based permission-role mappings.
3. Generates:
   - A comprehensive `model.json` file for each project.
   - CSV reports per project, including:
     - `hosted_services-REST.csv` for REST endpoints
     - `hosted_services-gRPC.csv` for gRPC services and their methods
     - `Dependent_services.csv` for inter-service dependencies
     - `Permission-role_matrix.csv` for role-to-permission mappings based on SAML configuration

This tool is useful for SOX compliance, IT Governance, and other internal audit processes that require up-to-date service inventories and access matrices.

---

## Installation

### CLI Tool

1. **Build the CLI tool (requires SBT):**

   ```bash
   sbt auditReportCli/assembly
   ```

The resulting JAR will be located at `cli/target/scala-2.12/audit-report-cli.jar`.

2. **Run the CLI:**

   ```bash
   java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
   ```

---

## Configuration

### CLI Tool Options

| Option           | Required | Description                                                                                       | Default                     |
|------------------|----------|---------------------------------------------------------------------------------------------------|-----------------------------|
| `--githubUrl`    | Yes      | The URL of the GitHub server (e.g., `https://github.com`).                                        | None                        |
| `--organization` | No       | The GitHub organization to process (e.g., `norwegian-cruise-line`).                               | `norwegian-cruise-line`     |
| `--output`       | Yes      | Directory where all reports will be saved.                                                        | `./output`                  |
| `--input`        | No       | A local folder containing pre-cloned repositories. If not provided, the tool clones from GitHub.   | `/tmp/cloned_repos`         |
| `--config`       | No       | A JSON configuration file specifying products and projects to process.                            | None                        |

If a config file is provided, only those products and their projects listed will be processed, ignoring other repositories under the organization.

---

### Configuration File

**Example `config.json`:**

```json
{
  "products": [
    {
      "product": "CVS",
      "projects": [
        "proxima-vacation-service"
      ]
    },
    {
      "product": "booking",
      "projects": [
        "zero-booking-headquarters"
      ]
    }
  ]
}
```

This configuration instructs the tool to process:
- Product `CVS` with project `proxima-vacation-service`
- Product `booking` with project `zero-booking-headquarters`

---

## Usage

### Example CLI Invocation

**Without a config file (process all repositories in an organization):**

```bash
java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
```

**With a config file (process specified products and projects only):**

```bash
java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports --config /tmp/config.json
```

---

### Workflow

1. **Clone Repositories:**  
   Clones either all repositories in the given organization or those specified in the config file.

2. **Parse and Extract Data:**  
   Parses `.proto` and `.scala` files to extract:
   - gRPC services and methods
   - REST endpoints
   - Dependencies between services
   - SAML configurations to build permission-role matrices

3. **Generate Reports:**  
   For each project:
   - `model.json` is generated with all details.
   - CSV files are generated inside `output/product/project/`:
      - `hosted_services-REST.csv`: Lists all REST endpoints and related info.
      - `hosted_services-gRPC.csv`: Lists all gRPC methods and their input/output types.
      - `Dependent_services.csv`: Shows which services this project calls, including endpoints and arguments.
      - `Permission-role_matrix.csv`: Shows role-to-permission mappings derived from SAML configurations.

**Directory Structure Example:**

```
/path/to/reports/
└── CVS/
    └── proxima-vacation-service/
        ├── model.json
        ├── hosted_services-REST.csv
        ├── hosted_services-gRPC.csv
        ├── Dependent_services.csv
        └── Permission-role_matrix.csv
```

---

### Example Output

**Input `.proto`:**
```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

**Output `model.json` (for `proxima-vacation-service`):**
```json
{
  "name": "proxima-vacation-service",
  "repository": "https://github.com/norwegian-cruise-line/proxima-vacation-service",
  "product": "CVS",
  "services": [
    {
      "name": "Greeter",
      "methods": [
        {
          "name": "SayHello",
          "inputType": "HelloRequest",
          "outputType": "HelloReply"
        }
      ]
    }
  ],
  "dependencies": [],
  "restEndpoints": [],
  "samlConfigurations": []
}
```

**Output `hosted_services-gRPC.csv`:**
```csv
product,service,Endpoint,Argument,Response,DefinedIn,ImplementedIn
CVS,proxima-vacation-service,SayHello,HelloRequest,HelloReply,greeter.proto,
```

---

## Logging & Debugging

For verbose logging, include:
```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar audit-report-cli.jar ...
```

Ensure `gh` and `jq` are installed if the CLI needs to dynamically list repositories.

---

## SBT Plugin (Optional Add-On)

While the CLI tool is the primary focus, a basic SBT plugin is available for integrating some auditing steps into your build. To use the plugin:

1. Add to `project/plugins.sbt`:
   ```sbt
   addSbtPlugin("com.ncl" % "protobuf-audit-plugin" % "0.1.0")
   ```
2. Enable in `build.sbt`:
   ```sbt
   enablePlugins(ProtobufAuditPlugin)
   ```

This plugin provides limited functionality compared to the CLI tool and serves as a helper for certain development workflows. It is not required for generating audit reports.