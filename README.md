Protobuf Audit Plugin
=====================

An SBT plugin to parse Protobuf files, extract services and RPC methods, and generate a CSV report for internal audit and compliance.

Features
--------

-   Recursively scans `.proto` files in the project.
-   Extracts services, methods, input, and output types.
-   Generates a CSV report in the `target` folder (or custom location).

Installation
------------

1.  Add the plugin to `project/plugins.sbt`:

    `addSbtPlugin("com.ncl" % "protobuf-audit-plugin" % "0.1.0")`

2.  Enable the plugin in `build.sbt`:

    `enablePlugins(ProtobufAuditPlugin)`

Configuration (Optional)
------------------------

-   Set the Protobuf source path:

    ```sbt
    protobufSourcePath := Some(baseDirectory.value / "proto")
    ```

-   Set the report output path:

    ```sbt
    auditReportOutputPath := baseDirectory.value / "audit-reports" / "protobuf-services.csv"
    ```

Usage
-----

Run the task:

```bash
sbt auditProtobufFiles
```

### Example Output

Input `.proto` file:

```proto
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

Generated CSV:


```csv
File,Service,Method,InputType,OutputType
/path/to/Greeter.proto,Greeter,SayHello,HelloRequest,HelloReply
```
