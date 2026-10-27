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
| 플러그인 JAR | `dist/dev.eclipseline_1.3.5.jar` |
| 설치 ZIP | `dist/paper-update-site-1.3.5.zip` |
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
