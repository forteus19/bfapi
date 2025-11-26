package dev.vuis.bfapi.http;

import com.boehmod.bflib.cloud.common.AbstractClanData;
import com.boehmod.bflib.cloud.common.player.status.PlayerStatus;
import dev.vuis.bfapi.auth.MsCodeWrapper;
import dev.vuis.bfapi.cloud.BfCloudData;
import dev.vuis.bfapi.cloud.BfConnection;
import dev.vuis.bfapi.cloud.BfPlayerData;
import dev.vuis.bfapi.cloud.BfPlayerInventory;
import dev.vuis.bfapi.data.MinecraftProfile;
import dev.vuis.bfapi.data.Serialization;
import dev.vuis.bfapi.util.Responses;
import dev.vuis.bfapi.util.Util;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
public final class BfApiInboundHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	public static final String AUTH_CALLBACK_PATH = "/server_auth_callback";

	private final MsCodeWrapper msCodeWrapper;
	public BfConnection connection = null;

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
		QueryStringDecoder qs = new QueryStringDecoder(msg.uri());
		String path = qs.path();
		boolean keepAlive = HttpUtil.isKeepAlive(msg);

		FullHttpResponse response = switch (path) {
			case AUTH_CALLBACK_PATH -> msCodeWrapper != null ? serverAuthCallback(ctx, msg, qs) : null;
			case "/api/v1/clan_data" -> clanData(ctx, msg, qs);
			case "/api/v1/cloud_data" -> cloudData(ctx, msg, qs);
			case "/api/v1/player_data" -> playerData(ctx, msg, qs);
			case "/api/v1/player_inventory" -> playerInventory(ctx, msg, qs);
			case "/api/v1/player_status" -> playerStatus(ctx, msg, qs);
			default -> null;
		};

		if (response == null) {
			response = Responses.error(
				ctx, msg,
				HttpResponseStatus.NOT_FOUND,
				"not_found"
			);
		}

		log.info("{} {} - {}", msg.method(), msg.uri(), response.status().code());

		if (keepAlive) {
			response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
			ctx.writeAndFlush(response);
		} else {
			ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
		}
	}

	private FullHttpResponse serverAuthCallback(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}

		if (msCodeWrapper.future().isDone()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.FORBIDDEN,
				"already_authenticated"
			);
		}

		if (!qs.parameters().containsKey("code")) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_code"
			);
		}
		if (!qs.parameters().containsKey("state")) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_state"
			);
		}

		String state = qs.parameters().get("state").getFirst();
		if (!state.equals(msCodeWrapper.state())) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.FORBIDDEN,
				"unexpected_state"
			);
		}

		String code = qs.parameters().get("code").getFirst();
		msCodeWrapper.future().complete(code);

		return Responses.string(
			ctx, msg,
			HttpResponseStatus.OK,
			"Authentication completed"
		);
	}

	private FullHttpResponse clanData(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}
		if (connection == null || !connection.isConnectedAndVerified()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.SERVICE_UNAVAILABLE,
				"cloud_disconnected"
			);
		}

		if (!qs.parameters().containsKey("uuid")) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_uuid"
			);
		}

		Optional<UUID> uuid = Util.parseUuidLenient(qs.parameters().get("uuid").getFirst());
		if (uuid.isEmpty()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"invalid_uuid"
			);
		}

		AbstractClanData data;
		try {
			data = connection.dataCache.clanData.get(uuid.orElseThrow())
				.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException e) {
			log.error("error while retrieving clan data", e);
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error"
			);
		} catch (TimeoutException e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"packet_timeout"
			);
		}

		return Responses.json(
			ctx, msg,
			HttpResponseStatus.OK,
			w -> Serialization.clan(w, data, connection.dataCache)
		);
	}

	private FullHttpResponse cloudData(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}
		if (connection == null || !connection.isConnectedAndVerified()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.SERVICE_UNAVAILABLE,
				"cloud_disconnected"
			);
		}

		BfCloudData data;
		try {
			data = connection.dataCache.cloudData.get()
				.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException e) {
			log.error("error while retrieving cloud data", e);
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error"
			);
		} catch (TimeoutException e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"packet_timeout"
			);
		}

		return Responses.json(
			ctx, msg,
			HttpResponseStatus.OK,
			w -> data.serialize(w, connection.dataCache)
		);
	}

	private FullHttpResponse playerData(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}
		if (connection == null || !connection.isConnectedAndVerified()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.SERVICE_UNAVAILABLE,
				"cloud_disconnected"
			);
		}

		boolean hasUuid = qs.parameters().containsKey("uuid");
		boolean hasName = qs.parameters().containsKey("name");
		if (!(hasUuid || hasName)) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_uuid_or_name"
			);
		}
		if (hasUuid && hasName) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"both_uuid_and_name"
			);
		}

		UUID uuid;
		if (hasUuid) {
			Optional<UUID> uuidParseResult = Util.parseUuidLenient(qs.parameters().get("uuid").getFirst());
			if (uuidParseResult.isEmpty()) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.BAD_REQUEST,
					"invalid_uuid"
				);
			}

			uuid = uuidParseResult.orElseThrow();
		} else {
			Optional<MinecraftProfile> profile;
			try {
				profile = MinecraftProfile.retrieveByName(qs.parameters().get("name").getFirst());
			} catch (IOException | InterruptedException e) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.INTERNAL_SERVER_ERROR,
					"profile_unavailable"
				);
			}
			if (profile.isEmpty()) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.NOT_FOUND,
					"profile_not_found"
				);
			}

			uuid = profile.orElseThrow().uuid();
		}

		BfPlayerData data;
		try {
			data = connection.dataCache.playerData.get(uuid)
				.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException e) {
			log.error("error while retrieving player data", e);
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error"
			);
		} catch (TimeoutException e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"packet_timeout"
			);
		}

		return Responses.json(
			ctx, msg,
			HttpResponseStatus.OK,
			data::serialize
		);
	}

	private FullHttpResponse playerInventory(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}
		if (connection == null || !connection.isConnectedAndVerified()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.SERVICE_UNAVAILABLE,
				"cloud_disconnected"
			);
		}

		boolean hasUuid = qs.parameters().containsKey("uuid");
		boolean hasName = qs.parameters().containsKey("name");
		if (!(hasUuid || hasName)) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_uuid_or_name"
			);
		}
		if (hasUuid && hasName) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"both_uuid_and_name"
			);
		}

		boolean includeUuid = false;
		if (qs.parameters().containsKey("include_uuid")) {
			try {
				includeUuid = Boolean.parseBoolean(qs.parameters().get("include_uuid").getFirst());
			} catch (Exception e) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.BAD_REQUEST,
					"invalid_include_uuid"
				);
			}
		}
		boolean includeDetails = false;
		if (qs.parameters().containsKey("include_details")) {
			try {
				includeDetails = Boolean.parseBoolean(qs.parameters().get("include_details").getFirst());
			} catch (Exception e) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.BAD_REQUEST,
					"invalid_include_details"
				);
			}
		}

		UUID uuid;
		if (hasUuid) {
			Optional<UUID> uuidParseResult = Util.parseUuidLenient(qs.parameters().get("uuid").getFirst());
			if (uuidParseResult.isEmpty()) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.BAD_REQUEST,
					"invalid_uuid"
				);
			}

			uuid = uuidParseResult.orElseThrow();
		} else {
			Optional<MinecraftProfile> profile;
			try {
				profile = MinecraftProfile.retrieveByName(qs.parameters().get("name").getFirst());
			} catch (IOException | InterruptedException e) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.INTERNAL_SERVER_ERROR,
					"profile_unavailable"
				);
			}
			if (profile.isEmpty()) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.NOT_FOUND,
					"profile_not_found"
				);
			}

			uuid = profile.orElseThrow().uuid();
		}

		BfPlayerInventory data;
		try {
			data = connection.dataCache.playerInventory.get(uuid)
				.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException e) {
			log.error("error while retrieving player data", e);
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error"
			);
		} catch (TimeoutException e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"packet_timeout"
			);
		}


		boolean finalIncludeUuid = includeUuid;
		boolean finalIncludeDetails = includeDetails;
		return Responses.json(
			ctx, msg,
			HttpResponseStatus.OK,
			w -> Serialization.playerInventory(
				w, data, connection.registry, finalIncludeUuid, finalIncludeDetails,
				Util.unchecked(w2 -> {
					w2.name("player").beginObject();
					Serialization.playerStub(w2, connection.dataCache, uuid);
					w2.endObject();
				})
			)
		);
	}

	private FullHttpResponse playerStatus(ChannelHandlerContext ctx, FullHttpRequest msg, QueryStringDecoder qs) {
		if (msg.method() != HttpMethod.GET) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.METHOD_NOT_ALLOWED,
				"method_not_allowed"
			);
		}
		if (connection == null || !connection.isConnectedAndVerified()) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.SERVICE_UNAVAILABLE,
				"cloud_disconnected"
			);
		}

		boolean hasUuid = qs.parameters().containsKey("uuid");
		boolean hasName = qs.parameters().containsKey("name");
		if (!(hasUuid || hasName)) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"missing_uuid_or_name"
			);
		}
		if (hasUuid && hasName) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.BAD_REQUEST,
				"both_uuid_and_name"
			);
		}

		UUID uuid;
		if (hasUuid) {
			Optional<UUID> uuidParseResult = Util.parseUuidLenient(qs.parameters().get("uuid").getFirst());
			if (uuidParseResult.isEmpty()) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.BAD_REQUEST,
					"invalid_uuid"
				);
			}

			uuid = uuidParseResult.orElseThrow();
		} else {
			Optional<MinecraftProfile> profile;
			try {
				profile = MinecraftProfile.retrieveByName(qs.parameters().get("name").getFirst());
			} catch (IOException | InterruptedException e) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.INTERNAL_SERVER_ERROR,
					"profile_unavailable"
				);
			}
			if (profile.isEmpty()) {
				return Responses.error(
					ctx, msg,
					HttpResponseStatus.NOT_FOUND,
					"profile_not_found"
				);
			}

			uuid = profile.orElseThrow().uuid();
		}

		PlayerStatus data;
		try {
			data = connection.dataCache.playerStatus.get(uuid)
				.get(10, TimeUnit.SECONDS);
		} catch (ExecutionException | InterruptedException e) {
			log.error("error while retrieving player data", e);
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"internal_server_error"
			);
		} catch (TimeoutException e) {
			return Responses.error(
				ctx, msg,
				HttpResponseStatus.INTERNAL_SERVER_ERROR,
				"packet_timeout"
			);
		}

		return Responses.json(
			ctx, msg,
			HttpResponseStatus.OK,
			w -> Serialization.playerStatus(
				w, data, connection.dataCache,
				Util.unchecked(w2 -> {
					w2.name("player").beginObject();
					Serialization.playerStub(w2, connection.dataCache, uuid);
					w2.endObject();
				})
			)
		);
	}
}
