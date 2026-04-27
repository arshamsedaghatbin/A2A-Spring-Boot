package com.bank.client.client;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Picks a fun financial tip based on what the agent is currently doing.
 */
public final class FunTips {

    private FunTips() {}

    private static final Random RNG = new Random();

    private static final Map<String, List<String>> TIPS = Map.of(

        "delegating", List.of(
            "💼 Opening the Digital Briefcase of Financial Destiny...",
            "🔍 Waking up the Banking Goblins (they're slow on Mondays)...",
            "📋 Consulting the Sacred Spreadsheet of Ancient Wisdom...",
            "🧙 Summoning the Council of Sub-Agents...",
            "⚙️  Spinning up the Money Processing Unit™..."
        ),

        "collecting", List.of(
            "🕵️  Deploying the Information Extraction Unit...",
            "📝 Cross-referencing with the Universal Suspicious Transactions Database...",
            "🧮 Verifying you didn't just mash the keyboard...",
            "🗂️  Filing your answers in the Cloud Folder of Truth...",
            "🤔 Thinking really hard about your money situation..."
        ),

        "checking", List.of(
            "✅ Double-checking... because banks love paperwork...",
            "📊 Auditing your soul (and your account number)...",
            "🔎 Looking for missing fields (and my car keys)...",
            "📌 Almost there — just triple-verifying, as per regulation 404...",
            "🧐 The quality inspector is being very judgmental right now..."
        ),

        "asking", List.of(
            "💬 Polishing the Question Crystals™...",
            "🗣️  Translating 'bank speak' to something resembling human...",
            "✍️  Crafting the Perfect Question (took 3 drafts)...",
            "🎤 The prompt-enhancer is clearing its throat...",
            "📢 Preparing to ask you something deeply personal about your finances..."
        ),

        "auth", List.of(
            "🔐 Whispering your name to the Security Gnomes...",
            "🕵️‍♂️ Checking if you're wanted in 7 countries (routine stuff)...",
            "👁️  Running a vibe check on your identity...",
            "🪪 Asking the bouncer if you're on the list...",
            "🛡️  Submitting your aura to the authentication oracle..."
        ),

        "balance", List.of(
            "💰 Peeking at your piggy bank (we promise not to judge)...",
            "🏧 Bribing the ATM for insider information...",
            "🤑 Counting your digital coins... trying not to be jealous...",
            "📈 Calculating your net worth (it's a short calculation)...",
            "🐷 Waking up the piggy bank. It's grumpy."
        ),

        "transfer", List.of(
            "🚀 Loading money into the Pneumatic Tube System™...",
            "💸 Teaching your cash to do a backflip mid-transfer...",
            "✈️  Folding your money into a paper airplane...",
            "🌊 Initiating the Great Cash Migration of " + java.time.Year.now() + "...",
            "📦 Wrapping your money in bubble wrap for the journey..."
        )
    );

    public static String forDelegating(String target) {
        if (target.contains("info-collector") || target.contains("bank-info")) return pick("collecting");
        if (target.contains("auth"))     return pick("auth");
        if (target.contains("balance"))  return pick("balance");
        if (target.contains("transfer")) return pick("transfer");
        return pick("delegating");
    }

    public static String forChecking()  { return pick("checking"); }
    public static String forAsking()    { return pick("asking"); }
    public static String forCalling()   { return pick("delegating"); }

    private static String pick(String key) {
        List<String> list = TIPS.getOrDefault(key, TIPS.get("delegating"));
        return list.get(RNG.nextInt(list.size()));
    }
}
