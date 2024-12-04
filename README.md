# Protobuf Audit Tool
=====================

The **Protobuf Audit Tool** provides both an **SBT plugin** and a **CLI tool** to parse Protobuf and Scala files, extract services and RPC methods, and generate reports for internal audit and compliance.

---

## Installation

### For the SBT Plugin

1. **Add the plugin to `project/plugins.sbt`:**

    ```sbt
    addSbtPlugin("com.ncl" % "protobuf-audit-plugin" % "0.1.0")
    ```

2. **Enable the plugin in your project's `build.sbt`:**

    ```sbt
    enablePlugins(ProtobufAuditPlugin)
    ```

---

### For the CLI Tool

1. **Download the standalone JAR:**

   Build the CLI tool using the following command:

   ```bash
   sbt auditReportCli/assembly
   ```

   The resulting JAR will be located at `cli/target/scala-2.12/audit-report-cli.jar`.

2. **Run the CLI:**

   ```bash
   java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
   ```

   **Example Input:**
   - The CLI will clone all repositories under the specified organization (e.g., `norwegian-cruise-line`).
   - It will process the repositories' Protobuf and Scala files and generate the reports in `/path/to/reports`.

---

## Configuration

### CLI Tool Options

The CLI supports the following options:

| Option         | Required | Description                                                                                   | Default                |
|----------------|----------|-----------------------------------------------------------------------------------------------|------------------------|
| `--githubUrl`  | Yes      | The URL of the GitHub server to clone repositories (e.g., `https://github.com`).              | None                  |
| `--organization` | NO       | The organization name to fetch repositories from (e.g., `norwegian-cruise-line`).            | `norwegian-cruise-line`                  |
| `--output`     | Yes      | Path to the folder where reports will be saved.                                               | `./output`                  |
| `--input`      | No       | A local folder containing already cloned repositories. If not specified, repositories will be cloned automatically. | `/tmp/cloned_repos`   |
| `--config`     | No       | Path to a configuration file specifying the product name and repositories to process.          | None                  |

---

### Configuration File

You can specify repositories to process using a configuration file in JSON format. If a configuration file is provided, it overrides the need to fetch all repositories under the organization.

#### Configuration File Example (JSON)

```json
{
  "product": "CVS",
  "projects": [
    "proxima-vacation-service",
    "another-service"
  ]
}
```

- **`product`**: The name of the product or category the repositories belong to. Used to organize the output.
- **`projects`**: A list of repositories to clone and process.

---

## Usage

### Example: CLI Tool Workflow

**Input Repositories on GitHub:**
- Organization: `norwegian-cruise-line`
- Repository Example: `proxima-vacation-service`

#### Command:

```bash
java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
```

#### Using a Config File:

```bash
java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports --config /tmp/config.json
```

---

### Workflow

1. Clones repositories specified in the configuration file or all repositories under the organization if no config is provided.
2. Recursively scans `.proto` and `.scala` files in each cloned repository.
3. Extracts services, methods, input types, and output types.
4. Generates a JSON report for each repository in `/path/to/reports`.

#### Output Structure (with Configuration File):

The output folder will have a hierarchy based on the `product` and `projects` from the configuration file:

```
/path/to/reports/
└── CVS/
    ├── proxima-vacation-service/
    │   └── model.json
    └── another-service/
        └── model.json
```

---

### Example Output

**Input `.proto` file:**

```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

**Generated JSON (`proxima-vacation-service_model.json`):**

```json
{
  "name": "proxima-vacation-service",
  "repository": "git@github.com:norwegian-cruise-line/proxima-vacation-service.git",
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
  "dependencies": []
}
```

---

## Notes

- The CLI tool relies on external tools (`gh` and `jq`) to list repositories and parse JSON. Ensure they are installed and available in your `PATH`.
- For more detailed logging, you can pass the `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug` JVM option to enable debug logging. For example:

```bash
java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug -jar audit-report-cli.jar ...
```