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

   The resulting JAR will be located at `cli/target/scala-2.13/audit-report-cli.jar`.

2. **Run the CLI:**

   ```bash
   java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
   ```

   **Example Input:**
    - The CLI will clone all repositories under the specified organization (e.g., `norwegian-cruise-line`).
    - It will process the repositories' Protobuf and Scala files and generate the reports in `/path/to/reports`.

---

## Configuration (Optional)

### For the SBT Plugin

- **Set the Protobuf source path**:

    ```sbt
    protobufSourcePath := Some(baseDirectory.value / "proto")
    ```

- **Set the report output path**:

    ```sbt
    auditReportOutputPath := baseDirectory.value / "audit-reports" / "protobuf-services.csv"
    ```

### For the CLI Tool

The CLI supports the following options:

- **`--githubUrl`**: Required. The URL of the GitHub server to clone repositories (e.g., `https://github.com`).
- **`--organization`**: Required. The organization name to fetch repositories from (e.g., `norwegian-cruise-line`).
- **`--output`**: Required. Path to the folder where reports will be saved.
- **`--input`**: Optional. A local folder containing already cloned repositories. If not specified, repositories will be cloned automatically.

---

## Usage

### SBT Plugin

Run the task:

```bash
sbt auditProtobufFiles
```

### CLI Tool

Run the tool on a folder of repositories cloned from GitHub:

```bash
java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
```

---

### Example: CLI Tool Workflow

**Input Repositories on GitHub:**
- Organization: `norwegian-cruise-line`
- Repository Example: `proxima-vacation-service`

**Command:**

```bash
java -jar audit-report-cli.jar --githubUrl https://github.com --organization norwegian-cruise-line --output /path/to/reports
```

**Workflow:**
1. Clones all repositories from the organization `norwegian-cruise-line`.
2. Recursively scans `.proto` and `.scala` files in each cloned repository.
3. Extracts services, methods, input types, and output types.
4. Generates a JSON report for each repository in `/path/to/reports`.

**Output:**
For each repository, a report file named `<repository-name>_model.json` is generated in the specified output folder.

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
