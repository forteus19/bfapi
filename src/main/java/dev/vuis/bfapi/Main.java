package dev.vuis.bfapi;

import dev.vuis.bfapi.auth.MicrosoftAuth;
import dev.vuis.bfapi.auth.MsCodeFuture;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiHttpHandler;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class Main {
	private static final int PORT = Integer.parseInt(Util.getEnvOrThrow("PORT"));
	private static final String MS_CLIENT_ID = Util.getEnvOrThrow("MS_CLIENT_ID");
	private static final String MS_TENANT_ID = Util.getEnvOrThrow("MS_TENANT_ID");
	private static final String MS_REDIRECT_HOST = Util.getEnvOrThrow("MS_REDIRECT_HOST");

    private Main() {
    }

    public static void main(String[] args) {
		CompletableFuture<String> msCodeFuture = new CompletableFuture<>();
		String state = MicrosoftAuth.randomState();

		startHttpServer(new MsCodeFuture(msCodeFuture, state));

        MicrosoftAuth msAuth = new MicrosoftAuth(MS_CLIENT_ID, MS_TENANT_ID);

		System.out.println(msAuth.getAuthUri(
			MS_REDIRECT_HOST + BfApiHttpHandler.AUTH_CALLBACK_PATH,
			MicrosoftAuth.XBOX_LIVE_SCOPE,
			state
		));
		try {
			System.out.println(msCodeFuture.get());
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	private static void startHttpServer(MsCodeFuture msCodeFuture) {
		EventLoopGroup elg = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

		ServerBootstrap bootstrap = new ServerBootstrap()
			.group(elg)
			.channel(NioServerSocketChannel.class)
			.childHandler(new BfApiChannelInitializer(msCodeFuture));

		bootstrap.bind(PORT).syncUninterruptibly();
	}
}
