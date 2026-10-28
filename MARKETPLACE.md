# Eclipse Marketplace 등록

## 업데이트 사이트 게시

1. `docs/updates/`를 포함해 GitHub `main` 브랜치에 push합니다.
2. **Settings → Pages → Deploy from a branch → main → /docs → Save**를 설정합니다.
3. Pages 배포 완료 후 Eclipse의 **Help → Install New Software… → Add…**에 다음 주소를 입력합니다.
4. 목록에서 **Paper 1.3.13**를 선택해 설치합니다.

```text
https://pinnpublic.github.io/eclipse-paper/updates/
```

## Marketplace 등록

1. [Eclipse Marketplace](https://marketplace.eclipse.org/)에 로그인합니다.
2. 새 솔루션 등록 화면을 엽니다.
3. 아래 등록 정보를 입력합니다.
4. Update Site의 feature 목록에서 **Paper**를 선택합니다.
5. 화면 예시와 영문 소개문을 추가하고 등록을 완료합니다.

| 항목 | 값 |
|---|---|
| Name / Title | **Paper** |
| Version | `1.3.13` |
| Organization / Provider | `pinnpublic` |
| License | **MIT License** |
| Homepage / Source | `https://github.com/pinnpublic/eclipse-paper` |
| Documentation | `https://github.com/pinnpublic/eclipse-paper#readme` |
| Support / Issues | `https://github.com/pinnpublic/eclipse-paper/issues` |
| Update Site | `https://pinnpublic.github.io/eclipse-paper/updates/` |
| Feature ID | `dev.eclipseline.feature` |
| p2 Installable Unit | `dev.eclipseline.feature.feature.group` |
| Bundle ID | `dev.eclipseline` |
| Platform | Windows |
| Minimum Eclipse / Java | Eclipse 4.21 / Java 11 |
| Screenshots | `docs/images/light-editor.png`, `docs/images/dark-editor.png` |

## 영문 소개문

### Short description

Notebook paper backgrounds for Eclipse source editors.

### Description

- 10 line styles and 12 background patterns: ruled notebooks, grids, dots and Cornell notes.
- 35 paper textures: crumpled paper, Xuan paper and handmade materials.
- Separate light and dark paper colors.
- Adjustable line thickness, text-to-rule spacing and line spacing.
- Adjustable current-line highlight opacity.
- Tab-aligned notebook and Cornell margins.
- Rules and patterns that scale with editor font size.

**Settings:** Window → Preferences → General → Editors → Text Editors → Paper / 노트 배경.

**Editors:** Java, Text Editor, XML/JSP source pages.

**Platform:** Windows. Eclipse 4.21 or later. Java 11 or later.

**License:** MIT.

## 다음 버전 배포

같은 Update Site 주소를 유지하면 Pages의 p2 저장소가 새 설치 버전을 제공합니다. Marketplace 소개 페이지의 버전·설명·지원 Eclipse 버전은 GitHub README와 별개이므로 해당 등록 화면에서 갱신합니다.

1. bundle·feature·category와 빌드·검사 스크립트의 버전을 갱신합니다.
2. [빌드·검사 절차](DEVELOPMENT.md)를 실행합니다.
3. 소스와 `docs/updates/`를 함께 push합니다.
4. Pages 배포 후 공개 업데이트 주소에서 새 버전을 설치합니다.
5. GitHub Release에 ZIP과 SHA-256 파일을 첨부합니다.
6. Marketplace 버전 정보를 갱신합니다.

## 참고

- [GitHub Pages 배포 설정](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)
- [Eclipse p2 Publisher](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html)
