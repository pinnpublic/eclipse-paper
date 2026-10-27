# Eclipse Marketplace 등록 안내

GitHub 소스 저장소: https://github.com/pinnpublic/eclipse-paper

## 먼저 업데이트 사이트 게시

1. `docs/updates/`에 `content.jar`, `artifacts.jar`, `features/`, `plugins/`가 있는지 확인합니다.
2. 이 폴더를 포함해 GitHub `main` 브랜치에 push합니다.
3. GitHub **Settings → Pages → Deploy from a branch → main → /docs**를 선택하고 저장합니다.
4. Pages 배포가 끝나면 Eclipse의 **Help → Install New Software… → Add…**에서 다음 주소를 확인합니다.

```text
https://pinnpublic.github.io/eclipse-paper/updates/
```

**Paper 1.3.5**가 나타나고 설치가 완료되는 것을 확인한 뒤 Marketplace에 등록합니다. GitHub 저장소 주소나 ZIP 다운로드 주소를 Update Site 항목에 넣지 않습니다. p2 메타데이터와 플러그인 JAR를 읽을 수 있는 저장소 루트 주소를 사용합니다.

Pages가 아직 활성화되지 않았다면 위 주소는 동작하지 않습니다. 로컬에서 수행한 ZIP/p2 검증이 공개 HTTPS 설치 검증을 대신하지는 않습니다.

## 등록 항목

| 항목 | 값 |
|---|---|
| Name / Title | **Paper** |
| Version | `1.3.5` |
| Organization / Provider | `pinnpublic` |
| License | **MIT License** |
| Homepage / Source | `https://github.com/pinnpublic/eclipse-paper` |
| Documentation | `https://github.com/pinnpublic/eclipse-paper#readme` |
| Support / Issues | `https://github.com/pinnpublic/eclipse-paper/issues` |
| Update Site | `https://pinnpublic.github.io/eclipse-paper/updates/` |
| Feature ID | `dev.eclipseline.feature` |
| p2 Installable Unit | `dev.eclipseline.feature.feature.group` |
| Bundle ID | `dev.eclipseline` |
| Tested platforms | Windows |
| Minimum baseline | Eclipse 4.21 / Java 11 |
| Screenshots | `docs/images/light-editor.png`, `docs/images/dark-editor.png` |

Marketplace에서 사이트를 읽어 feature 목록을 제공하면 **Paper**를 선택합니다. 직접 ID를 입력해야 한다면 해당 필드가 feature ID인지 p2 IU인지 구분해 위 값을 사용합니다. 내부 ID는 이전 개발 버전과의 업데이트 호환성을 위해 유지합니다.

Eclipse 계정으로 [Eclipse Marketplace](https://marketplace.eclipse.org/)에 로그인해 새 솔루션 등록을 진행합니다. 라이선스·플랫폼·호환 버전은 실제 확인 범위에 맞춰 입력합니다. Linux/macOS는 아직 실행 검증하지 않았습니다.

## 영문 소개문

### Short description

Notebook rules, grids and paper textures for Eclipse source editors, with light and dark theme support.

### Description

Paper turns Eclipse and Spring Tools source editors into a configurable notebook surface while preserving native text editing, syntax highlighting, line numbers and ruler input.

- 10 line styles and 12 background patterns, including ruled notebooks, grids, dots and Cornell notes.
- 35 paper textures, including crumpled paper, Xuan paper and handmade materials.
- Separate light and dark paper colors that follow the active Eclipse theme.
- Font-aware rules, pattern sizes, independent line spacing and text-to-rule spacing.
- Adjustable current-line highlight opacity.
- Tab-aligned notebook and Cornell margins.
- Native-resolution rendering for zoomed fonts and high-DPI displays.

Configure Paper under Window → Preferences → General → Editors → Text Editors → Paper / 노트 배경.

Paper targets SWT StyledText source editors, including Java, the default Text Editor and XML/JSP source pages. It does not modify source file contents. Tested on Windows with STS 3.9.18, Eclipse 4.40 and Spring Tools for Eclipse 5.3.0. Requires Eclipse 4.21 or later and Java 11 or later; the IDE itself may require a newer Java runtime.

Licensed under the MIT License.

## 다음 버전 배포

1. bundle/feature/category와 빌드·검사 스크립트의 버전을 함께 올립니다.
2. `build.ps1`, `build-update-site.ps1`을 실행합니다. 두 번째 스크립트가 검증한 p2 파일을 `docs/updates/`에 준비합니다.
3. 검사 결과를 확인하고 소스·p2 메타데이터·plugin/feature JAR를 같은 커밋으로 push합니다.
4. Pages 배포 후 공개 URL에서 새 버전의 설치·업데이트를 확인합니다.
5. GitHub Release에 ZIP과 SHA-256 파일을 첨부하고 Marketplace 버전 정보를 갱신합니다.

이미 배포한 같은 버전의 JAR를 다른 내용으로 덮어쓰지 않습니다. 기존 JAR는 캐시된 설치 요청을 위해 유지하고 새 버전의 메타데이터와 JAR를 게시합니다.

참고: [GitHub Pages 배포 소스](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site), [Eclipse p2 Publisher](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html), [Marketplace 데이터의 updateurl / ius 항목](https://webdev.eclipse.org/docs/api/marketplace-rest-api).
