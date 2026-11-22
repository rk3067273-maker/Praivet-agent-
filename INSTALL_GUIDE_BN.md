# J.A.R.V.I.S. (PrivateAgent) — Android ফোনে ডাউনলোড ও ইনস্টল গাইড (বাংলা)

কম্পিউটার (PC) ছাড়াই সরাসরি আপনার Android ফোন থেকে GitHub-এ গিয়ে J.A.R.V.I.S. অ্যাপ ডাউনলোড ও ইনস্টল করার সম্পূর্ণ নিয়ম নিচে দেওয়া হলো।

---

## ১. GitHub থেকে সঠিক APK ডাউনলোড করার ধাপ

1. ফোনের ব্রাউজার (যেমন Chrome) দিয়ে GitHub রিপোজিটরি খুলুন:
   - `https://github.com/AbuZar-Ansarii/Jarvis`
2. পেজের নিচে বা ডানদিকে **Releases** সেকশনে ট্যাপ করুন (অথবা সরাসরি খুলুন: `https://github.com/AbuZar-Ansarii/Jarvis/releases`)।
3. সবার উপরের **Latest** (সবুজ লেবেলযুক্ত **Stable Release**) বেছে নিন। *(Pre-release বা Development Build এড়িয়ে চলুন, যদি না আপনি নিজে পরীক্ষা করতে চান।)*
4. **Assets** অংশে ট্যাপ করে খুলুন এবং সঠিক APK ফাইলটি ডাউনলোড করুন:
   - আধুনিক প্রায় সব ৬৪-বিট Android ফোনের জন্য: **`JARVIS-v<version>-arm64-v8a.apk`** (সবচেয়ে হালকা ও দ্রুত)।
   - ফোনের প্রসেসর (ABI) সম্পর্কে নিশ্চিত না হলে: **`JARVIS-v<version>-universal.apk`**।
   - পুরনো ৩২-বিট ফোনের জন্য: **`JARVIS-v<version>-armeabi-v7a.apk`**।
5. ডাউনলোড শেষ হলে ফাইলের নাম মিলিয়ে দেখুন (`JARVIS-v...apk` এবং ফাইল সাইজ `0 bytes` নয়)। চাইলে রিলিজ পেজের **`SHA256SUMS.txt`** ফাইলের সাথে SHA-256 চেকসাম মিলিয়ে নিশ্চিত হতে পারেন।

---

## ২. ফোনে অ্যাপ ইনস্টল করার নিয়ম

1. ব্রাউজারের **Downloads** অপশন বা ফোনের **Files / File Manager** অ্যাপ খুলে ডাউনলোড করা `.apk` ফাইলে ট্যাপ করুন।
2. Android যদি বলে **"For your security, your phone isn't allowed to install unknown apps from this source"**:
   - পপ-আপে **Settings**-এ ট্যাপ করুন।
   - শুধুমাত্র ওই ব্রাউজার বা File Manager অ্যাপটির জন্য **Allow from this source** চালু করুন এবং Back করুন।
   - *(নিরাপত্তার স্বার্থে Google Play Protect কখনোই পুরোপুরি বন্ধ করবেন না।)*
3. এবার **Install** (বা আপডেটের ক্ষেত্রে **Update**) বোতামে ট্যাপ করুন।
4. ইনস্টলেশন শেষ হলে **Open** চাপুন এবং J.A.R.V.I.S. চালু করুন।

---

## ৩. প্রথমবার চালু করার পর AI সেটআপ ও পারমিশন

1. **AI Provider সেটআপ:**
   - নিচের মেনু থেকে **Agent** ট্যাবে যান।
   - আপনি চাইলে ডিফল্ট **Gemini (Firebase AI)** ব্যবহার করতে পারেন, অথবা **OpenAI**, **DeepSeek**, **OpenRouter**, **NVIDIA NIM**, **Local Ollama**, বা **Custom** HTTPS প্রোভাইডার বেছে নিয়ে আপনার ব্যক্তিগত API Key সেভ করুন।
   - **Test Connection** বোতামে ট্যাপ করে সংযোগ যাচাই করুন।
2. **প্রয়োজনীয় পারমিশন (শুধুমাত্র দরকারিগুলো দিন):**
   - **Microphone (`RECORD_AUDIO`):** কেবল ভয়েস কমান্ড বা *"Hey Jarvis"* ওয়েক-ওয়ার্ড ব্যবহার করতে চাইলে দিন।
   - **Camera (`CAMERA`):** কেবল Sentry Mode-এ স্বয়ংক্রিয় ছবি তুলতে চাইলে দিন।
   - **Notifications (`POST_NOTIFICATIONS`):** Sentry সতর্কতা নোটিফিকেশন পেতে চাইলে দিন।
   - ছবি আপলোডের জন্য অ্যাপটি Android-এর নিরাপদ **Photo Picker** ব্যবহার করে—তাই পুরো গ্যালারি/স্টোরেজ পারমিশন দেওয়ার দরকার নেই।

---

## ৪. সাধারণ সমস্যা ও সমাধান (Troubleshooting)

| সমস্যা | সম্ভাব্য কারণ ও সমাধান |
| --- | --- |
| **App not installed / ইনস্টল হচ্ছে না** | ডাউনলোড অসম্পূর্ণ হতে পারে অথবা ফোনে স্টোরেজ কম থাকতে পারে। অন্তত 200 MB ফ্রি জায়গা রেখে ফাইলটি আবার ডাউনলোড করুন। |
| **Package conflicts with an existing package / Signature mismatch** | ফোনে আগে ভিন্ন Signing Key বা ভিন্ন Build-এর (যেমন Debug APK বা পুরনো Flutter APK) একই অ্যাপ ইনস্টল করা থাকতে পারে। গুরুত্বপূর্ণ তথ্য থাকলে আগে **Vault -> Export Sanitized Backup** দিয়ে ব্যাকআপ নিন, তারপর আগের ভার্সনটি আনইনস্টল করে নতুন রিলিজ APK ইনস্টল করুন। |
| **There was a problem parsing the package / Unsupported Android version** | এই অ্যাপটির জন্য কমপক্ষে **Android 7.0 (API 24)** প্রয়োজন। পুরনো Android সংস্করণে এটি চলবে না। |
| **Device architecture / Incorrect ABI** | আপনার ফোনে `arm64-v8a` কাজ না করলে রিলিজ পেজ থেকে `JARVIS-v...-universal.apk` ডাউনলোড করে ইনস্টল করুন। |
| **Download interrupted / ডাউনলোড আটকে যায়** | স্থিতিশীল Wi-Fi বা মোবাইল ডেটায় ফিরে ব্রাউজারের Download ম্যানেজার থেকে ফাইলটি নতুন করে ডাউনলোড করুন (Resume না করে Fresh Download করুন)। |
| **Installation blocked by Android / Play Protect সতর্কতা** | Play Protect কখনোই সম্পূর্ণ বন্ধ (Disable) করবেন না। শুধু নিশ্চিত হোন আপনি সরাসরি অফিশিয়াল GitHub Releases (`AbuZar-Ansarii/Jarvis`) থেকে আসল রিলিজ-সাইন করা APK ডাউনলোড করেছেন। যদি Debug APK হয় তবে তা ইনস্টল করবেন না—সর্বদা **Signed Release APK** ব্যবহার করুন। |
| **Storage full / অপর্যাপ্ত মেমোরি** | ফোনের Settings -> Storage থেকে অপ্রয়োজনীয় ক্যাশ/বড় ফাইল সরিয়ে জায়গা খালি করে আবার চেষ্টা করুন। |
