package simplewins;

import build.buf.protovalidate.Validator;
import build.buf.protovalidate.ValidatorFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

public class ValidatorProducer {

    @Produces
    @ApplicationScoped
    public Validator validator() {
        return ValidatorFactory.newBuilder().build();
    }

}
