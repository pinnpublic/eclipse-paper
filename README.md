# Paper

**Eclipse/STS 소스 편집기를 공책처럼 표시하는 플러그인입니다.** 선·모눈·종이 질감을 조합하고 라이트/다크 테마별 종이색을 지정할 수 있습니다. 소스 파일의 내용과 인코딩은 변경하지 않습니다.

현재 버전 **1.3.5** · [MIT License](LICENSE) · [변경 내역](CHANGELOG.md) · [Issues](https://github.com/pinnpublic/eclipse-paper/issues)

<img src="docs/images/light-editor.png" alt="라이트 테마의 Paper" width="48%"> <img src="docs/images/dark-editor.png" alt="다크 테마의 Paper" width="48%">

## 설치

### 업데이트 사이트로 설치

1. Eclipse/STS에서 **Help → Install New Software… → Add…**를 엽니다.
2. 이름은 `Paper`, Location은 아래 주소로 입력합니다.
3. **Paper**를 선택하고 설치를 완료한 뒤 IDE를 재시작합니다.

```text
https://pinnpublic.github.io/eclipse-paper/updates/
```

이 주소는 저장소의 **GitHub Pages를 활성화한 뒤** 사용할 수 있습니다. Pages 활성화 방법은 아래 [GitHub·Marketplace 배포](#githubmarketplace-배포)를 참고하세요.

Marketplace 등록이 완료되면 **Help → Eclipse Marketplace…**에서 `Paper`를 찾아 설치할 수도 있습니다. 등록 전에는 검색 결과에 나타나지 않습니다.

### ZIP으로 설치

[GitHub Releases](https://github.com/pinnpublic/eclipse-paper/releases)에 첨부된 `paper-update-site-1.3.5.zip`을 받거나 직접 빌드합니다.

1. **Help → Install New Software… → Add… → Archive…**를 엽니다.
2. ZIP을 압축을 풀지 않고 선택합니다.
3. **Paper**를 설치하고 IDE를 재시작합니다.

서명되지 않은 로컬 빌드에는 설치 중 신뢰 확인이 표시될 수 있습니다. 사용하는 IDE 설치본마다 설치하며, 설정은 워크스페이스별로 저장됩니다.

## 처음 사용하기

1. **Window → Preferences → General → Editors → Text Editors → Paper / 노트 배경**을 엽니다. Preferences 검색창에서 `Paper` 또는 `노트`로 찾아도 됩니다.
2. **소스 편집기에 노트 표시**를 켭니다.
3. **선** 탭에서 선 종류·색·간격을 정합니다.
4. **배경** 탭에서 공책·모눈 등의 무늬와 종이 질감을 선택합니다.
5. 미리보기에서 확인하고 **Apply** 또는 **Apply and Close**를 누릅니다.

예를 들어 배경을 **공책**, 질감을 **화선지**로 고르고 기본 선 간격을 유지하면 종이 질감과 가로줄·탭 기준 세로선이 함께 표시됩니다. 질감을 고르면 어울리는 라이트·다크 종이색이 자동으로 채워지며 이후 각 색을 따로 바꿀 수 있습니다.

## 설정 매뉴얼

### 선과 줄 간격

| 옵션 | 범위 / 동작 |
|---|---|
| 모든 글줄 아래에 선 표시 | 가로줄 표시를 켜거나 끕니다. |
| 선 종류 | 실선, 가는 점선, 둥근 점선, 짧은 파선, 긴 파선, 일점쇄선, 이점쇄선, 이중선, 삼중선, 물결선의 10종입니다. |
| 선 색상 | 가로줄 색을 지정합니다. |
| 두께 | 글자 높이의 1~25%입니다. |
| 텍스트와 선 간격 | 글자 높이의 **-50~100%**입니다. 음수는 선을 글자 쪽으로 올립니다. |
| 추가 줄 간격 | 글자 높이의 **0~200%**를 기존 줄 간격에 더합니다. 0은 편집기 기본 간격입니다. 기본값은 30%입니다. |
| 선 진하기 | 1~100%입니다. |
| 현재 줄 강조 불투명도 | **0~100%**, 기본값 50%입니다. 0은 완전 투명, 100은 불투명입니다. |

**텍스트와 선 간격**은 선의 위치를 바꾸고, **추가 줄 간격**은 글줄 사이의 높이를 바꿉니다. 서로 독립적이며 선을 숨겨도 추가 줄 간격은 유지됩니다.

### 배경과 테마

| 옵션 | 범위 / 동작 |
|---|---|
| 배경 종류 | 단색, 공책, 모눈, 촘촘한 모눈, 공학용 모눈, 점 격자, 십자 격자, 세로줄, 등각 격자, 마름모 격자, 원고지, 코넬 노트의 12종입니다. |
| 편집기 테마의 배경색 사용 | 켜면 편집기의 테마 배경색을 사용합니다. 직접 종이색을 지정하려면 끕니다. |
| 종이 배경색 — 라이트 / 다크 | 두 색을 따로 저장하며 현재 Eclipse 테마에 맞는 색을 적용합니다. |
| 무늬 색상 | 모눈·점·격자 등의 색을 지정합니다. |
| 무늬 크기 | 줄 높이의 50~300%입니다. |
| 무늬 진하기 | 1~100%입니다. |
| 종이 질감 | 35종과 `없음`을 제공합니다. |
| 질감 진하기 | 0~100%입니다. 0이면 질감이 보이지 않습니다. |

공책의 세로선은 실제 **탭 1개 위치에서 왼쪽으로 5px**, 코넬 노트는 **탭 2개 위치에서 왼쪽으로 5px** 이동한 곳에 표시됩니다. 탭 폭·글꼴·가로 스크롤이 바뀌면 위치도 따라 바뀝니다.

라이트·다크의 **종이색은 별도**이고 선 종류·선 색상·무늬·질감·간격 설정은 공통입니다. 현재 줄 강조 아래로 종이와 세로선이 비치며, 여러 줄 텍스트 선택은 편집기 기본 동작을 유지합니다.

### 종이 질감 35종

| 분류 | 질감 |
|---|---|
| 기본 종이 | 매끈한 백상지, 고운 미색지, 거친 종이, 재생지, 크라프트지, 한지, 수채화지, 린넨지, 양피지, 코튼지, 신문지 |
| 구김·접힘 | 가볍게 구긴 종이, 거칠게 구긴 종이, 펼친 구김 종이, 사등분 접은 종이, 아코디언 접지 |
| 동양·수제 종이 | 화선지, 생화선지, 숙화선지, 닥종이, 운용지, 대나무 종이, 볏짚 종이, 바나나 섬유지, 로크타 수제지 |
| 특수 표면 | 골판지, 엠보싱 종이, 레이드지, 트레이싱지, 감열지, 얼룩진 빈티지 종이, 대리석 마블지, 청색 도화지, 회색 스케치지, 유산지 |

질감은 배경 무늬와 함께 사용할 수 있습니다. 글꼴 확대/축소 시 선과 무늬는 글자 크기에 맞춰 다시 그리며, 종이 질감의 크기는 글꼴과 독립적으로 유지합니다. 구김 종이·화선지 자산의 제작 내역은 [assets/paper/README.md](assets/paper/README.md)에 있습니다.

### 줄 번호와 확대/축소

- 줄 번호와 기본 오류 표시 영역에도 종이 배경을 이어 표시합니다.
- 줄 번호의 글꼴·기준선·마우스 동작은 네이티브 편집기를 따릅니다. 가장자리는 종이색과 합성해 단색 테두리를 줄입니다.
- 첫 행 위에는 글자 높이에 비례한 상단 여백을 둡니다.
- 선과 무늬는 화면 배율에 맞춰 원본 해상도로 다시 그립니다.
- 미리보기의 확대/축소는 설정 화면에만 적용됩니다. 실제 편집기 글꼴 크기는 Eclipse의 글꼴 설정이나 해당 편집기의 확대/축소 기능을 사용합니다.

## 업데이트·끄기·삭제

- **업데이트:** Help → Check for Updates를 사용하거나 같은 업데이트 사이트에서 새 버전을 설치하고 재시작합니다.
- **일시 해제:** Preferences에서 **소스 편집기에 노트 표시**를 끄고 Apply를 누릅니다. 기존 배경·추가 전 줄 간격으로 복원합니다.
- **삭제:** Help → About Eclipse IDE(제품에 따라 About Spring Tools) → Installation Details → Installed Software에서 **Paper**를 선택해 Uninstall하고 재시작합니다.

이전 개발 버전에서 업그레이드해도 내부 ID와 설정 키는 유지됩니다. 기존 설정을 삭제할 필요가 없습니다.

## 지원 환경과 확인 범위

최소 기준은 **Eclipse 4.21 / Java 11**입니다. 아래 Windows 환경에서 실행을 확인했습니다.

| 제품 | Eclipse | 시험 Java |
|---|---|---|
| Spring Tool Suite 3.9.18 | 4.21 | 11 |
| Eclipse IDE | 4.40 | 21 |
| Spring Tools for Eclipse 5.3.0 | 4.40 | 21 |

SWT StyledText와 공개 텍스트 편집기 어댑터를 제공하는 **Java·기본 Text Editor·XML/JSP 소스 페이지**가 대상입니다. 일반 View·콘솔·검색 입력창이나 자체 웹 렌더러 편집기는 포함하지 않습니다. Linux/macOS는 실행 검증하지 않았습니다. 편집기 자체의 불투명한 줄 강조·코드 마이닝·가변 줄 간격에는 해당 편집기의 표시가 우선될 수 있습니다.

[검증 범위](docs/verification.md)를 참고하세요.

## 빌드와 검사

Windows PowerShell, JDK 11 이상, 로컬 Eclipse/STS 설치본이 필요합니다. Maven이나 외부 의존성 다운로드는 사용하지 않습니다. 공통 API 기준은 STS 3/Eclipse 4.21이며 Java 11 바이트코드로 빌드합니다.

아래 경로는 본인의 설치 경로로 바꿉니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-21'
powershell -NoProfile -ExecutionPolicy Bypass -File .\build-update-site.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
```

결과는 `dist/paper-update-site-1.3.5.zip`입니다. 두 번째 스크립트는 p2 메타데이터와 실제 파일 수집을 검증한 뒤 `docs/updates/`에도 업데이트 저장소를 준비합니다. 기존 ZIP에서 저장소만 다시 준비하려면 `prepare-update-site.ps1`을 실행합니다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-ruler.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-rendering.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
powershell -NoProfile -ExecutionPolicy Bypass -File .\test-runtime.ps1 -EclipseHome 'C:\tools\sts-3.9.18.RELEASE' -JdkHome 'C:\tools\jdk-11'
```

`test-runtime.ps1`은 별도 구성·워크스페이스를 만듭니다. 최신 Eclipse/STS 5를 시험할 때는 Java 21 이상을 사용합니다. 임시 결과는 `.build/`, 배포 ZIP은 `dist/`에 생성됩니다.

## GitHub·Marketplace 배포

소스 저장소는 **https://github.com/pinnpublic/eclipse-paper**입니다.

1. GitHub에 소스와 **`docs/updates/` 전체**를 올립니다. JAR 파일도 필수 배포 파일이므로 포함합니다.
2. 저장소 **Settings → Pages → Deploy from a branch → main → /docs → Save**를 설정합니다.
3. Pages 배포 완료 후 업데이트 주소를 Eclipse의 Install New Software에서 확인합니다.
4. GitHub Release에 `dist/paper-update-site-1.3.5.zip`과 `.sha256`을 첨부하면 ZIP 설치도 제공할 수 있습니다.
5. Eclipse Marketplace에 **Paper**로 등록하고 업데이트 사이트를 연결합니다.

등록용 항목과 영문 소개문은 [MARKETPLACE.md](MARKETPLACE.md)에 준비했습니다. GitHub 업로드, Pages 배포, Marketplace 등록은 각각 별도 단계입니다.

GitHub Pages 설정은 [GitHub 공식 안내](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site), p2 저장소 구성은 [Eclipse 공식 안내](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html)를 참고하세요.

## 폴더 구성

| 경로 | 용도 |
|---|---|
| `src/`, `META-INF/`, `plugin.xml` | 플러그인 소스와 등록 정보 |
| `assets/` | 원본 종이 질감과 제작 내역 |
| `feature/` | 설치 feature·분류·MIT 라이선스 |
| `docs/updates/` | GitHub Pages에 게시할 p2 저장소 — Git에 포함 |
| `docs/images/` | 매뉴얼 화면 예시 |
| `tests/`, `test-*.ps1` | 렌더링·자원·실제 IDE 검사 |
| `.build/`, `dist/` | 재생성 가능한 결과 — Git 제외 |
| `local/`, `releases/` | 개발 환경 기록·과거 검증·동결 보관본 — Git 제외 |

현재 소스와 이미지 자산은 [MIT License](LICENSE)로 배포합니다. 이 프로젝트는 독립적인 플러그인이며 Eclipse Foundation의 공식 제품이 아닙니다.
