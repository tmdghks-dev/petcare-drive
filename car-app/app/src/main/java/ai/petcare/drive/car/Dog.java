package ai.petcare.drive.car;

import java.util.List;

// 반려견 프로필. 적정 온도로 대기 화면 안내 문구를 만든다.
// TODO: 지금은 앱 안에 고정. Firebase 연결 후 폰 앱에서 등록한 프로필을 받아오도록 교체
final class Dog {
    final String name;
    final int photoRes;
    final int bustRes; // 배경을 지운 상반신 사진 (대기 화면용). 없으면 0 → 동그란 사진을 쓴다
    final String breed;
    final String age;
    final int tempMin, tempMax;
    final boolean anxious;

    Dog(String name, int photoRes, int bustRes, String breed, String age, int tempMin, int tempMax, boolean anxious) {
        this.name = name;
        this.photoRes = photoRes;
        this.bustRes = bustRes;
        this.breed = breed;
        this.age = age;
        this.tempMin = tempMin;
        this.tempMax = tempMax;
        this.anxious = anxious;
    }

    static final List<Dog> ALL = List.of(
            new Dog("달이", R.drawable.dali, R.drawable.dali_bust, "[견종]", "[나이]", 22, 24, true));

    // 받침 유무에 맞는 조사: josa("달이", "은", "는") → 달이는, josa("별", "은", "는") → 별은
    static String josa(String name, String withBatchim, String without) {
        char last = name.charAt(name.length() - 1);
        boolean batchim = last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 != 0;
        return name + (batchim ? withBatchim : without);
    }
}
