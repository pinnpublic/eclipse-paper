# 빌드·검사

## 준비

- Windows PowerShell
- JDK 11 이상
- STS 3.9.18 / Eclipse 4.21 설치본
- 최신 Eclipse/STS 5 실행 검사: Java 21 이상

명령의 설치 경로를 본인 환경에 맞게 지정합니다.

## 빌드

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-21'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-update-site.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
```

| 결과 | 경로 |
|---|---|
| 플러그인 JAR | `dist/dev.eclipseline_1.3.12.jar` |
| 설치 ZIP | `dist/paper-update-site-1.3.12.zip` |
| SHA-256 | 각 파일의 `.sha256` |
| 온라인 p2 저장소 | `docs/updates/` |

기존 ZIP에서 p2 저장소를 준비하려면 다음 명령을 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\prepare-update-site.ps1
```

## 검사

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-ruler.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-rendering.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-runtime.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
```

| 스크립트 | 검사 |
|---|---|
| `test-ruler.ps1` | 줄 번호·기준선·입력·이미지 자원·창 복원 |
| `test-rendering.ps1` | 선·무늬·질감·투명도·배율 |
| `test-runtime.ps1` | 실제 IDE의 편집기·설정·테마·마커 |
| `test-runtime.ps1 -GutterOnly` | 큰 글꼴·선택·편집 후 접기 영역의 종이 연결과 실제 접기·펼치기 클릭 |
| `test-runtime.ps1 -GutterOnly -ThemeOnly` | 표시 정리·원본 모델 복원 검사와 라이트·다크 테마·오류 아이콘 회귀 검사 |
| `test-runtime.ps1 -ScrollOnly` | 긴 문서·큰 글꼴에서 실제 Windows 휠 입력 후 번호와 본문 픽셀 기준선 비교 |

1.3.12의 `test-ruler.ps1`에는 본문/줄 번호의 그리기 순서가 바뀌어도 같은 프레임을 유지하는 검사,
정수 행 스크롤의 이미지 재사용, 부분 행의 가로줄 갱신, 테마가 지운 이미지의 재부착,
레이어 캐시 키의 원본/정규화 픽셀·알파 비교 12,960개가 포함됩니다.
기본 노트 스타일에서 같은 픽셀의 종이 이미지를 재생성하지 않는 것이 목표이며,
변경된 본문과 숫자의 native paint 및 합성은 계속 필요합니다.

`tests/ScreenScrollTest.java`는 `Control.print` 없이 화면을 읽는 별도 검사입니다.
화면 읽기가 백지를 반환하거나 창이 가려졌다면 검사 불가로 취급하며,
일반 픽셀 회귀 검사 통과를 실제 화면 증상 해결의 근거로 대신하지 않습니다.

## 폴더

| 경로 | 용도 |
|---|---|
| `src/`, `META-INF/`, `plugin.xml` | 플러그인 소스·등록 정보 |
| `assets/` | 종이 질감 |
| `feature/` | 설치 feature·분류·라이선스 |
| `docs/updates/` | 배포용 p2 저장소 |
| `tests/` | 검사 소스 |
| `.build/`, `dist/` | 빌드 결과 |
| `local/`, `releases/` | 로컬 보관 자료 |

배포 절차: [MARKETPLACE.md](MARKETPLACE.md)
