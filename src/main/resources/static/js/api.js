(function () {
  async function request(url, options) {
    const response = await fetch(url, options);
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || 'Yêu cầu không thành công.');
    return data;
  }

  window.meetingApi = {
    getParticipants: () => request('/api/participants'),
    findAvailability: (payload) => request('/api/availability', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
  };
}());
