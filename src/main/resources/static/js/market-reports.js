(() => {
    const page = document.getElementById('market-report-page');
    if (!page) {
        return;
    }

    const tabs = Array.from(page.querySelectorAll('[role="tab"][data-topic]'));
    const state = document.getElementById('market-report-state');
    const newsList = document.getElementById('market-report-news');
    const title = document.getElementById('market-report-title');
    const symbol = document.getElementById('market-report-symbol');
    const category = document.getElementById('market-report-category');
    const description = document.getElementById('market-report-description');
    const updatedAt = document.getElementById('market-report-updated-at');
    const query = document.getElementById('market-report-query');
    const keywords = document.getElementById('market-report-keywords');
    const responseCache = new Map();
    let activeTopic = null;
    let requestSequence = 0;

    const decodeText = (value) => {
        if (!value) {
            return '';
        }
        return new DOMParser().parseFromString(value, 'text/html').body.textContent || '';
    };

    const safeArticleUrl = (item) => {
        const candidate = item.originalLink || item.link;
        if (!candidate) {
            return null;
        }
        try {
            const url = new URL(candidate);
            return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
        } catch (error) {
            return null;
        }
    };

    const formatDateTime = (value) => {
        if (!value) {
            return '-';
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return new Intl.DateTimeFormat('ko-KR', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        }).format(date);
    };

    const setLoading = () => {
        state.replaceChildren();
        const loader = document.createElement('span');
        loader.className = 'report-loader';
        loader.setAttribute('aria-hidden', 'true');
        const heading = document.createElement('strong');
        heading.textContent = '시장 뉴스를 정리하고 있습니다.';
        const message = document.createElement('p');
        message.textContent = '관련도순 후보에서 핵심 뉴스를 선별합니다.';
        state.append(loader, heading, message);
        state.hidden = false;
        newsList.hidden = true;
    };

    const setEmptyState = () => {
        state.replaceChildren();
        const heading = document.createElement('strong');
        heading.textContent = '표시할 시장 뉴스가 없습니다.';
        const message = document.createElement('p');
        message.textContent = '다음 갱신 시점에 새로운 기사를 확인해 주세요.';
        state.append(heading, message);
        state.hidden = false;
        newsList.hidden = true;
    };

    const setErrorState = (topicCode) => {
        state.replaceChildren();
        const heading = document.createElement('strong');
        heading.textContent = '시장 뉴스를 불러오지 못했습니다.';
        const message = document.createElement('p');
        message.textContent = '잠시 후 다시 시도해 주세요.';
        const retry = document.createElement('button');
        retry.className = 'market-report-retry';
        retry.type = 'button';
        retry.textContent = '다시 시도';
        retry.addEventListener('click', () => loadReport(topicCode, true));
        state.append(heading, message, retry);
        state.hidden = false;
        newsList.hidden = true;
    };

    const renderKeywords = (items) => {
        keywords.replaceChildren();
        items.forEach((keyword) => {
            const chip = document.createElement('span');
            chip.textContent = keyword;
            keywords.appendChild(chip);
        });
    };

    const renderNews = (response) => {
        title.textContent = `${response.displayName} 시장 뉴스`;
        symbol.textContent = response.symbol;
        category.textContent = response.category;
        description.textContent = response.description;
        updatedAt.textContent = `업데이트 ${formatDateTime(response.updatedAt)}`;
        query.textContent = `검색 기준 ${response.query}`;
        renderKeywords(Array.isArray(response.keywords) ? response.keywords : []);

        newsList.replaceChildren();
        const items = Array.isArray(response.items) ? response.items : [];
        items.forEach((item, index) => {
            const article = document.createElement('article');
            article.className = 'market-report-card';

            const articleUrl = safeArticleUrl(item);
            const top = document.createElement('div');
            top.className = 'market-report-card-top';
            const source = document.createElement('span');
            source.className = 'market-report-source';
            source.textContent = articleUrl
                ? new URL(articleUrl).hostname.replace(/^www\./, '')
                : 'NAVER 뉴스 검색';
            const rank = document.createElement('span');
            rank.className = 'market-report-rank';
            rank.textContent = String(index + 1).padStart(2, '0');
            top.append(source, rank);
            article.appendChild(top);

            const heading = document.createElement('h3');
            if (articleUrl) {
                const headingLink = document.createElement('a');
                headingLink.href = articleUrl;
                headingLink.target = '_blank';
                headingLink.rel = 'noopener noreferrer';
                headingLink.textContent = decodeText(item.title);
                heading.appendChild(headingLink);
            } else {
                heading.textContent = decodeText(item.title);
            }
            article.appendChild(heading);

            if (item.description) {
                const summary = document.createElement('p');
                summary.className = 'market-report-summary';
                summary.textContent = decodeText(item.description);
                article.appendChild(summary);
            }

            const footer = document.createElement('div');
            footer.className = 'market-report-card-footer';
            const time = document.createElement('time');
            time.textContent = formatDateTime(item.publishedAt);
            footer.appendChild(time);
            if (articleUrl) {
                const originalLink = document.createElement('a');
                originalLink.href = articleUrl;
                originalLink.target = '_blank';
                originalLink.rel = 'noopener noreferrer';
                originalLink.textContent = '원문 보기';
                footer.appendChild(originalLink);
            }
            article.appendChild(footer);
            newsList.appendChild(article);
        });

        if (items.length === 0) {
            setEmptyState();
            return;
        }
        state.hidden = true;
        newsList.hidden = false;
    };

    const updateTopicHeading = (tab) => {
        page.dataset.activeTopic = tab.dataset.topic;
        title.textContent = `${tab.dataset.displayName} 시장 뉴스`;
        symbol.textContent = tab.dataset.symbol;
        category.textContent = tab.dataset.category;
        description.textContent = tab.dataset.description;
        updatedAt.textContent = '업데이트 확인 중';
        query.textContent = '검색 기준 확인 중';
        keywords.replaceChildren();
    };

    const loadReport = async (topicCode, forceReload = false) => {
        const sequence = ++requestSequence;
        if (!forceReload && responseCache.has(topicCode)) {
            renderNews(responseCache.get(topicCode));
            return;
        }

        setLoading();
        try {
            const response = await fetch(`${page.dataset.reportEndpoint}/${encodeURIComponent(topicCode)}`, {
                headers: { 'Accept': 'application/json' }
            });
            if (!response.ok) {
                throw new Error(`시장 리포트 요청 실패: ${response.status}`);
            }
            const data = await response.json();
            responseCache.set(topicCode, data);
            if (sequence === requestSequence && topicCode === activeTopic) {
                renderNews(data);
            }
        } catch (error) {
            if (sequence === requestSequence && topicCode === activeTopic) {
                setErrorState(topicCode);
            }
        }
    };

    const activateTab = (tab, moveFocus = false) => {
        activeTopic = tab.dataset.topic;
        tabs.forEach((candidate) => {
            const selected = candidate === tab;
            candidate.classList.toggle('is-active', selected);
            candidate.setAttribute('aria-selected', String(selected));
            candidate.tabIndex = selected ? 0 : -1;
        });
        updateTopicHeading(tab);
        if (moveFocus) {
            tab.focus();
        }
        loadReport(activeTopic);
    };

    tabs.forEach((tab, index) => {
        tab.addEventListener('click', () => activateTab(tab));
        tab.addEventListener('keydown', (event) => {
            if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
                return;
            }
            event.preventDefault();
            const direction = event.key === 'ArrowRight' ? 1 : -1;
            const nextIndex = (index + direction + tabs.length) % tabs.length;
            activateTab(tabs[nextIndex], true);
        });
    });

    const initialTab = tabs.find((tab) => tab.classList.contains('is-active')) || tabs[0];
    if (initialTab) {
        activateTab(initialTab);
    }
})();
