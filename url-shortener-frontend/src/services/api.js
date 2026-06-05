import axios from 'axios';

const API_BASE = '/api/v1';

export const shortenUrl = async (originalUrl, expiryDays = null, customAlias = null) => {
  const res = await axios.post(`${API_BASE}/shorten`, {
    originalUrl,
    expiryDays,
    customAlias: customAlias || null,
  });
  return res.data;
};

export const listUrls = async (page = 0, size = 10) => {
  const res = await axios.get(`${API_BASE}/urls`, { params: { page, size } });
  return res.data;
};

export const getAnalytics = async (shortCode) => {
  const res = await axios.get(`${API_BASE}/analytics/${shortCode}`);
  return res.data;
};

export const deleteUrl = async (shortCode) => {
  const res = await axios.delete(`${API_BASE}/urls/${shortCode}`);
  return res.data;
};
