
package com.codahale.metrics.riemann;

import com.aphyr.riemann.client.AbstractRiemannClient;

import org.junit.Test;

import static org.mockito.Mockito.*;

public class RiemannTest {
    private final AbstractRiemannClient client = mock(AbstractRiemannClient.class);
    private final Riemann riemann = new Riemann(client);

    @Test
    public void connectsToRiemann() throws Exception {
        riemann.connect();
        verify(client).connect();
    }

    @Test
    public void disconnectsFromRiemann() throws Exception {
        riemann.connect();
        riemann.close();
        verify(client).disconnect();
    }
}

