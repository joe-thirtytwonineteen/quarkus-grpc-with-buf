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
