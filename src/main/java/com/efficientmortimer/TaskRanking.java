package com.efficientmortimer;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TaskRanking
{
	static final int SKIP_COST = 100;
	private static final int MAX_IMPORT_LENGTH = 16_384;
	private final Map<String, Integer> ranks;
	private final int acceptedCount;

	private TaskRanking(Map<String, Integer> ranks, int acceptedCount)
	{
		this.ranks = Map.copyOf(ranks);
		this.acceptedCount = acceptedCount;
	}

	static TaskRanking parse(Gson gson, String input)
	{
		if (input == null || input.isBlank())
		{
			throw new IllegalArgumentException("Paste a ranking copied from ehp.gg.");
		}
		if (input.length() > MAX_IMPORT_LENGTH)
		{
			throw new IllegalArgumentException("Ranking is too long.");
		}

		JsonObject data;
		try (JsonReader reader = new JsonReader(new StringReader(input)))
		{
			data = gson.getAdapter(JsonObject.class).read(reader);
			if (data == null || reader.peek() != JsonToken.END_DOCUMENT)
			{
				throw new IllegalArgumentException("Paste a valid ranking export.");
			}
		}
		catch (IOException | JsonParseException | IllegalStateException e)
		{
			throw new IllegalArgumentException("Paste a valid ranking export.");
		}

		if (!"efficient-mortimer".equals(stringValue(data.get("type"))))
		{
			throw new IllegalArgumentException("This is not an Efficient Mortimer export.");
		}
		if (integerValue(data.get("version"), "Version") != 1)
		{
			throw new IllegalArgumentException("Unsupported export version. Update Efficient Mortimer.");
		}
		int acceptedCount = integerValue(data.get("acceptedCount"), "Accepted count");
		JsonElement ranking = data.get("ranking");
		if (ranking == null || !ranking.isJsonArray())
		{
			throw new IllegalArgumentException("Export is missing its task ranking.");
		}
		JsonArray entries = ranking.getAsJsonArray();
		if (entries.size() == 0 || entries.size() > TaskCatalog.getMaxRankingSize())
		{
			throw new IllegalArgumentException("Ranking must contain 1 to " + TaskCatalog.getMaxRankingSize() + " entries.");
		}
		if (acceptedCount < 0 || acceptedCount > entries.size())
		{
			throw new IllegalArgumentException("Accepted count is outside the ranking.");
		}

		Map<String, Integer> ranks = new HashMap<>();
		for (int i = 0; i < entries.size(); i++)
		{
			String key = stringValue(entries.get(i));
			if (!TaskCatalog.isValidKey(key))
			{
				throw new IllegalArgumentException("Unknown task or modifier at entry " + (i + 1) + ".");
			}
			if (ranks.putIfAbsent(key, i) != null)
			{
				throw new IllegalArgumentException("Duplicate task and modifier at entry " + (i + 1) + ".");
			}
		}
		return new TaskRanking(ranks, acceptedCount);
	}

	int getSize()
	{
		return ranks.size();
	}

	int getAcceptedCount()
	{
		return acceptedCount;
	}

	Recommendation recommend(List<String> offerKeys, int points)
	{
		if (offerKeys == null || offerKeys.size() < 2 || offerKeys.size() > 3)
		{
			return Recommendation.unavailable();
		}
		int bestSlot = -1;
		int bestRank = Integer.MAX_VALUE;
		for (int slot = 0; slot < offerKeys.size(); slot++)
		{
			String key = offerKeys.get(slot);
			Integer rank = key == null ? null : ranks.get(key);
			if (rank == null)
			{
				return Recommendation.unavailable();
			}
			if (rank < bestRank)
			{
				bestSlot = slot;
				bestRank = rank;
			}
		}
		if (bestRank < acceptedCount)
		{
			return new Recommendation(Recommendation.Action.TAKE, bestSlot);
		}
		if (points < 0)
		{
			return Recommendation.unavailable();
		}
		return new Recommendation(points >= SKIP_COST ? Recommendation.Action.SKIP : Recommendation.Action.TAKE,
			bestSlot);
	}

	private static String stringValue(JsonElement value)
	{
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
			? value.getAsString() : null;
	}

	private static int integerValue(JsonElement value, String field)
	{
		if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
		{
			try
			{
				return new BigDecimal(value.getAsString()).intValueExact();
			}
			catch (NumberFormatException | ArithmeticException e)
			{
				throw new IllegalArgumentException(field + " must be a whole number.");
			}
		}
		throw new IllegalArgumentException(field + " must be a whole number.");
	}
}
