document.addEventListener('DOMContentLoaded', () => {
    const map = L.map('map').setView([40.5, -105.5], 10);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

    fetch('/collections/features/items')
        .then(response => response.json())
        .then(data => {
            L.geoJSON(data).addTo(map);
            document.getElementById('json-output').textContent = JSON.stringify(data, null, 2);
        });
});
