(() => {
    const cards = [...document.querySelectorAll('.learning-card')];
    const filters = [...document.querySelectorAll('[data-learning-filter]')];
    const searchInput = document.getElementById('learning-search-input');
    const empty = document.getElementById('learning-empty');
    const dialog = document.getElementById('investment-learning-dialog');
    if (!dialog) return;

    let activeCategory = 'ALL';
    const filterCards = () => {
        const query = (searchInput?.value || '').trim().toLocaleLowerCase('ko-KR');
        let visible = 0;
        cards.forEach((card) => {
            const categoryMatches = activeCategory === 'ALL' || card.dataset.category === activeCategory;
            const text = `${card.dataset.title} ${card.dataset.summary}`.toLocaleLowerCase('ko-KR');
            const matches = categoryMatches && (!query || text.includes(query));
            card.hidden = !matches;
            if (matches) visible += 1;
        });
        if (empty) empty.hidden = visible > 0;
    };

    filters.forEach((filter) => filter.addEventListener('click', () => {
        activeCategory = filter.dataset.learningFilter;
        filters.forEach((item) => {
            const selected = item === filter;
            item.classList.toggle('is-active', selected);
            item.setAttribute('aria-pressed', String(selected));
        });
        filterCards();
    }));
    searchInput?.addEventListener('input', filterCards);

    const title = document.getElementById('learning-dialog-title');
    const body = document.getElementById('learning-dialog-body');
    const status = document.getElementById('learning-dialog-status');
    const summary = document.getElementById('learning-dialog-summary');
    const visual = document.getElementById('learning-dialog-visual');
    const visualImage = document.getElementById('learning-dialog-visual-image');
    const visualCaption = document.getElementById('learning-dialog-visual-caption');
    const example = document.getElementById('learning-dialog-example');
    const explanation = document.getElementById('learning-dialog-explanation');
    const marketImpact = document.getElementById('learning-dialog-market-impact');
    const marketImpactSection = document.getElementById('learning-dialog-market-impact-section');
    const caution = document.getElementById('learning-dialog-caution');

    const setStatus = (message) => {
        status.textContent = message;
        status.hidden = false;
        body.hidden = true;
    };
    const hasFormulaContent = (paragraphText) => {
        const explicitFormula = /[=÷×→]/.test(paragraphText);
        const formulaLabel = /(?:핵심 흐름|핵심 관계|핵심 차이|핵심 해석|시장 영향|초보자 핵심|계산 예시|계산식|산식|공식|목표금액|필요한 증거금|추가로 준비할 금액|비용 전 손익|괴리율|원화 환산가치|명목금액|손익분기)/.test(paragraphText);
        const numericCalculation = /\d[\d,.]*(?:만|천|억|조)?\s*(?:원|주|포대|달러|%|배).*(?:차이|차익|빼면|나누|곱하|더하|손익|순이익|손실|수익|가치|금액|비중|남는|됩니다|입니다)/.test(paragraphText);
        return explicitFormula || formulaLabel || numericCalculation;
    };
    const renderFormulaParagraph = (paragraph, paragraphText) => {
        const formulaParts = paragraphText.match(/^(공식|계산식|계산 예시|숫자 대입|비중 계산|손익 계산|결과|핵심 흐름|핵심 관계|핵심 차이|핵심 해석|시장 영향|초보자 핵심):\s*(.+)$/);
        if (!formulaParts) {
            paragraph.textContent = paragraphText;
            return;
        }

        const label = document.createElement('span');
        label.className = 'learning-formula-label';
        label.textContent = formulaParts[1];

        const expression = document.createElement('span');
        expression.className = 'learning-formula-expression';
        expression.textContent = formulaParts[2];

        paragraph.append(label, expression);
    };
    const renderParagraphs = (container, text) => {
        const fragment = document.createDocumentFragment();
        String(text || '')
            .split(/\n+/)
            .map((paragraph) => paragraph.trim())
            .filter(Boolean)
            .forEach((paragraphText) => {
                const paragraph = document.createElement('p');
                if (hasFormulaContent(paragraphText)) {
                    paragraph.classList.add('learning-detail-formula');
                    renderFormulaParagraph(paragraph, paragraphText);
                } else {
                    paragraph.textContent = paragraphText;
                }
                fragment.appendChild(paragraph);
            });
        container.replaceChildren(fragment);
    };
    const render = (concept) => {
        title.textContent = concept.title;
        summary.textContent = concept.summary;
        renderParagraphs(example, concept.bakeryExample);
        renderParagraphs(explanation, concept.detailedExplanation);
        const hasMarketImpact = Boolean(concept.marketImpact?.trim());
        marketImpactSection.hidden = !hasMarketImpact;
        if (hasMarketImpact) renderParagraphs(marketImpact, concept.marketImpact);
        renderParagraphs(caution, concept.caution);
        if (concept.visual?.assetPath) {
            visualImage.src = concept.visual.assetPath;
            visualImage.alt = concept.visual.altText || '';
            visualCaption.textContent = concept.visual.caption || '';
            visual.hidden = false;
        } else {
            visual.hidden = true;
        }
        status.hidden = true;
        body.hidden = false;
    };

    cards.forEach((card) => card.addEventListener('click', async () => {
        title.textContent = card.dataset.title;
        setStatus('개념을 불러오는 중입니다.');
        dialog.showModal();
        try {
            const response = await fetch(`/api/investment-learning/concepts/${encodeURIComponent(card.dataset.conceptCode)}`,
                    {headers: {'Accept': 'application/json'}});
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            render(await response.json());
        } catch (error) {
            setStatus('개념을 불러오지 못했습니다. 개념 동기화 상태를 확인한 뒤 다시 시도해 주세요.');
        }
    }));
    document.getElementById('learning-dialog-close')?.addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (event) => { if (event.target === dialog) dialog.close(); });

    const resizer = document.getElementById('learning-drawer-resizer');
    let resizing = false;
    resizer?.addEventListener('pointerdown', (event) => {
        resizing = true;
        resizer.setPointerCapture(event.pointerId);
        document.body.classList.add('learning-resizing');
    });
    resizer?.addEventListener('pointermove', (event) => {
        if (!resizing) return;
        const width = Math.max(460, Math.min(window.innerWidth - 48, window.innerWidth - event.clientX));
        dialog.style.setProperty('--learning-drawer-width', `${width}px`);
    });
    const stopResize = () => { resizing = false; document.body.classList.remove('learning-resizing'); };
    resizer?.addEventListener('pointerup', stopResize);
    resizer?.addEventListener('pointercancel', stopResize);
})();
