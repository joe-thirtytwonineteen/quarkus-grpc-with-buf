I come from old-school applicationContext.xml Spring (and then Grails, and the Micronaut) roots, but I've found I enjoy Quarkus' stripped-down approach, similar to the same way I like Micronaut.

One thing that I really like about it is the out-of-the-box live reloading: it's such a time saver, one that I've missed in other languages. I started using it with a vanilla gRPC service a few weeks ago and wanted to see if I could get it working with Buf instead of `protoc`, and found out it was pretty easy.

This means that I get a modern toolchain for Protobuf - Buf picks up where Google left off, bringing in package management (list other stuff here...) 



Let's get started.

4. Start with default template from https://code.quarkus.io/. I've used Gradle + Kotlin as my build tool.
2. Add dependencies to build.gradle.kts:
   implementation("io.quarkus:quarkus-grpc")
   implementation("io.quarkus:quarkus-rest")
   implementation("javax.annotation:javax.annotation-api")
   implementation("build.buf:protovalidate:1.1.0")
3. Note that "the code generation goals/tasks are automatically executed when using the default lifecycle of the plugins" - that's what we don't want. We want Buf to handle code generation, proto dependency resolution, linting, breaking change detection, etc. Not bare-bones .proto text file editing, but treating it like a first class language. 
   1. Buf needs to be installed
   2. Disable code gen in application.properties: quarkus.grpc.codegen.skip=true
4. Add a `buf.yaml`. Let's go ahead and include Protovalidate. I'll be blunt: dependency resolution and including things like Protovalidate easily are enough on their own to move me to Buf. If you're just in Quarkus, it's more complicated. If you're in an environment where you need to span services, languages, and build systems, which is pretty likely if you're using proto, it's :good: to move outside of a language-specific framework.

```
# For details on buf.yaml configuration, visit https://buf.build/docs/configuration/v2/buf-yaml
version: v2
modules:
  - path: src/main/proto
# It's much easier to rely on packages and deal with dependencies with the Buf
# CLI than raw protoc. Adding this in Gradle requires multiple edits!
deps:
  - buf.build/bufbuild/protovalidate  
lint:
  use:
    - STANDARD
breaking:
  use:
    - FILE
```

Configure code generation. We're not going to rely on protoc or having any of the basic gRPC plugins installed: we'll use Buf's ability to do this in the cloud, for us, on-demand.

But take a look at the last plugin: Quarkus' gRPC support wraps a special plugin, focused on Mutiny. It's available locally through a wrapper script (thanks, Claude, because I stink at bash). Buf'll happily execute it as well, so we get the same Mutiny-focused handler stubs we're used to in Quarkus.

```
version: v2
inputs:
  - directory: src/main/proto
plugins:
  - remote: buf.build/protocolbuffers/java:v29.3
    out: .
  - remote: buf.build/grpc/java:v1.70.0
    out: .
  - local: ./protoc-gen-grpc-java-mutiny
    out: .    
managed:
  enabled: true
  override:
    - file_option: java_package_suffix
      value: gen
    - file_option: java_package_prefix
      value: ""
  disable:
    - file_option: java_package
      module: buf.build/bufbuild/protovalidate
```

5. Add the Buf Gradle plugin and include generated sources:
```kotlin
plugins {
   java
   id("io.quarkus")
   id("build.buf") version "0.11.0"
}

// Add a task dependency for compilation
tasks.named("compileJava").configure { dependsOn("bufGenerate") }

// Add the generated code to the main source set
sourceSets["main"].java { srcDir(layout.buildDirectory.dir("bufbuild/generated")) }
```

6. create some proto. note that we do NOT use file options: language-specific stuff is all handled by Buf!

```
syntax = "proto3";

// Buf handles this for us: no pollution of proto with language-specific concerns!
// option java_multiple_files = true;
// option java_package = "io.quarkus.example";
// option java_outer_classname = "HelloWorldProto";

package helloworld.v1;

// The greeting service definition.
service Greeter {
    // Sends a greeting
    rpc SayHello (HelloRequest) returns (HelloResponse) {}
}

// The request message containing the user's name.
message HelloRequest {
    string name = 1;
}

// The response message containing the greetings
message HelloResponse {
    string message = 1;
}
```

7. `buf lint` will point us to some best practices:

```
% buf lint
src/main/proto/helloworld/v1/greeter_service.proto:11:9:Service name "Greeter" should be suffixed with "Service".
src/main/proto/helloworld/v1/greeter_service.proto:13:17:RPC request type "HelloRequest" should be named "SayHelloRequest" or "GreeterSayHelloRequest".
src/main/proto/helloworld/v1/greeter_service.proto:13:40:RPC response type "HelloReply" should be named "SayHelloResponse" or "GreeterSayHelloResponse".
```

We'll fix those, and use the Buf CLI's BSR-powered dependency resolution to bring in Protovalidate, adding a rule to enforce a minimum length:

```
syntax = "proto3";

package helloworld.v1;

// Our buf.yaml's "deps" make this available, like Gradle or npm would in other
// languages.
import "buf/validate/validate.proto";

// The greeting service definition.
service GreeterService {
  // Sends a greeting
  rpc SayHello (SayHelloRequest) returns (SayHelloResponse) {}
}

// The request message containing the user's name.
message SayHelloRequest {
  // Let's add validation.
  string name = 1 [
    (buf.validate.field).string = {
      min_len: 1,
      max_len: 50
    }
  ];
}

// The response message containing the greetings
message SayHelloResponse {
  string message = 1;
}

```

8. Ok, generate code and compile:

```console
./gradlew build
```

It'll fail:

```console
Execution failed for task ':bufFormatCheck'.
> Some Protobuf files had format violations:
  diff -u src/main/proto/helloworld/v1/greeter_service.proto.orig src/main/proto/helloworld/v1/greeter_service.proto
  --- src/main/proto/helloworld/v1/greeter_service.proto.orig   2026-02-03 13:54:07.313550489 -0500
  +++ src/main/proto/helloworld/v1/greeter_service.proto        2026-02-03 13:54:07.313698741 -0500
```

Buf to the rescue:

```
./gradlew :bufFormatApply
```

Now, build again:

```console
./gradlew build
```

9. Great, now let's implement our service. Except for some naming (I have my preferences), it's as you'd expect: the same as the official Quarkus quickstart:

```java
package simplewins.helloworld.v1;

import helloworld.v1.gen.SayHelloRequest;
import helloworld.v1.gen.SayHelloResponse;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;

@GrpcService
public class GreeterService implements helloworld.v1.gen.GreeterService {
    @Override
    public Uni<SayHelloResponse> sayHello(SayHelloRequest request) {
        return Uni.createFrom().item(() ->
                SayHelloResponse.newBuilder().setMessage("Hello " + request.getName()).build()
        );
    }
}
```

10. Let's run it

```console
./gradlew quarkusDev
```

11. Call it. There's no need to write code any more: Buf includes a curl command that can speak gRPC or connect!

```console
buf curl \
  --schema ./src/main/proto \
  --protocol grpc \
  --http2-prior-knowledge \
  --data '{"name":"R2"}' \
  http://localhost:9000/helloworld.v1.GreeterService/SayHello
```

That works!

```console
{
  "message": "Hello R2"
}
```

12. Validate it! 

The full code for this contains a Quarkusly-modified version of Buf's [example Protovalidate interceptor](https://github.com/bufbuild/buf-examples/blob/main/protovalidate/grpc-java/finish/src/main/java/buf/build/example/protovalidate/ValidationInterceptor.java) for gRPC.

If you add it, those validation annotations in the schema are enforced. Send an invalid message and you'll see it enforce the semantic validation rules in your schema:

```console
buf curl \
  --schema ./src/main/proto \
  --protocol grpc \
  --http2-prior-knowledge \
  --data '{}' \
  http://localhost:9000/helloworld.v1.GreeterService/SayHello
```

13. Conclusion

...all the happy stuff you get from Buf, familiar quarkus workflow, even live reloading of gen code (change request.getName() to request.getName().toLowerCase() and retry, show lower case "r2")...





