package dev.vuis.bfapi;

import dev.vuis.bfapi.auth.MicrosoftAuth;
import dev.vuis.bfapi.auth.MinecraftAuth;
import dev.vuis.bfapi.auth.MsCodeFuture;
import dev.vuis.bfapi.auth.XblAuth;
import dev.vuis.bfapi.auth.XstsAuth;
import dev.vuis.bfapi.http.BfApiChannelInitializer;
import dev.vuis.bfapi.http.BfApiInboundHandler;
import dev.vuis.bfapi.util.Util;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Main {
	private static final int PORT = Integer.parseInt(Util.getEnvOrThrow("PORT"));
	private static final String MS_CLIENT_ID = Util.getEnvOrThrow("MS_CLIENT_ID");
	private static final String MS_CLIENT_SECRET_FILE = Util.getEnvOrThrow("MS_CLIENT_SECRET_FILE");
	private static final String MS_TENANT_ID = Util.getEnvOrThrow("MS_TENANT_ID");
	private static final String MS_REDIRECT_HOST = Util.getEnvOrThrow("MS_REDIRECT_HOST");

    private Main() {
    }

	@SneakyThrows
    public static void main(String[] args) {
		CompletableFuture<String> msCodeFuture = new CompletableFuture<>();
		String msState = MicrosoftAuth.randomState();

		log.info("Starting HTTP server");
		startHttpServer(new MsCodeFuture(msCodeFuture, msState));

		String msClientSecret;
        try {
            msClientSecret = Files.readString(Path.of(MS_CLIENT_SECRET_FILE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        MicrosoftAuth msAuth = new MicrosoftAuth(
			MS_CLIENT_ID,
			msClientSecret,
			MS_TENANT_ID,
			MS_REDIRECT_HOST + BfApiInboundHandler.AUTH_CALLBACK_PATH
		);

		log.info("Microsoft auth URL: {}", msAuth.getAuthUri(MicrosoftAuth.XBOX_LIVE_SCOPE, msState));

		String msAuthorizationCode = msCodeFuture.get();
		msAuth.redeemCode(msAuthorizationCode, false);

		XblAuth xblAuth = new XblAuth(msAuth);
		XstsAuth xstsAuth = new XstsAuth(xblAuth);
		MinecraftAuth mcAuth = new MinecraftAuth(xstsAuth);

		log.info(mcAuth.tokenOrRefresh());
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
