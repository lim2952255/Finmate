(() => {
    document.querySelectorAll('[data-currency-switcher]').forEach((switcher) => {
        const values = Array.from(switcher.querySelectorAll('[data-currency-value]'));
        const options = Array.from(switcher.querySelectorAll('[data-currency-option]'));

        if (values.length === 0) {
            return;
        }

        const preferredValue = values.find((value) => value.dataset.currency === 'KRW') || values[0];

        const selectCurrency = (currency) => {
            values.forEach((value) => {
                value.classList.toggle('is-active', value.dataset.currency === currency);
            });
            options.forEach((option) => {
                const active = option.dataset.currency === currency;
                option.classList.toggle('is-active', active);
                option.setAttribute('aria-pressed', String(active));
            });
        };

        options.forEach((option) => {
            option.addEventListener('click', () => selectCurrency(option.dataset.currency));
        });

        selectCurrency(preferredValue.dataset.currency);
    });
})();
