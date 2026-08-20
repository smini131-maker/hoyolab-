# org.json과 AndroidX WorkManager는 소비자 규칙을 제공한다.
# 앱 코드에는 리플렉션 기반 직렬화가 없어 별도의 광범위 keep 규칙이 필요 없다.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
