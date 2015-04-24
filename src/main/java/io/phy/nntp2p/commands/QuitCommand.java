package io.phy.nntp2p.commands;

import io.phy.nntp2p.connection.ClientChannel;
import io.phy.nntp2p.connection.ConnectionState;
import io.phy.nntp2p.protocol.ClientCommand;
import io.phy.nntp2p.protocol.NNTPReply;
import io.phy.nntp2p.protocol.NntpWriter;

import java.io.IOException;

public class QuitCommand implements ICommandImplementation {
    @Override
    public String CommandName() {
        return "QUIT";
    }

    @Override
    public boolean RequiresAuthentication() {
        return false;
    }

    @Override
    public void Handle(ClientChannel channel, ConnectionState state, ClientCommand command) throws IOException {
        state.setQuitting(true);
        NntpWriter.WriteServerReply(channel, NNTPReply.CLOSING_CONNECTION);

    }
}
