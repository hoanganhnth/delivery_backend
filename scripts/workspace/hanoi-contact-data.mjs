import { createHash } from 'node:crypto';

const DISTRICT_CENTROIDS = {
  'Hoàn Kiếm': [21.0285, 105.8542],
  'Tây Hồ': [21.0703, 105.8185],
  'Ba Đình': [21.0338, 105.8266],
  'Thanh Xuân': [20.9950, 105.8137],
  'Hai Bà Trưng': [20.9991, 105.8549],
  'Đống Đa': [21.0150, 105.8268],
  'Cầu Giấy': [21.0367, 105.7936],
  'Hoàng Mai': [20.9820, 105.8570],
  'Long Biên': [21.0457, 105.8803],
  'Hà Đông': [20.9710, 105.7786],
  'Nam Từ Liêm': [21.0140, 105.7650],
  'Bắc Từ Liêm': [21.0710, 105.7580],
  'Gia Lâm': [21.0130, 105.9550],
  'Hoài Đức': [21.0250, 105.7080],
  'Thanh Trì': [20.9460, 105.8430],
  'Thường Tín': [20.8430, 105.8630],
};

const DISTRICT_STREETS = {
  'Hoàn Kiếm': ['Hàng Bông', 'Hàng Bè', 'Hàng Tre', 'Phủ Doãn', 'Lý Quốc Sư', 'Hàng Buồm'],
  'Tây Hồ': ['Âu Cơ', 'Thụy Khuê', 'Yên Phụ', 'Nghi Tàm', 'Võ Chí Công'],
  'Ba Đình': ['Đội Cấn', 'Quán Thánh', 'Phan Đình Phùng', 'Giảng Võ', 'Văn Cao'],
  'Thanh Xuân': ['Vương Thừa Vũ', 'Nguyễn Xiển', 'Giáp Nhất', 'Khương Đình'],
  'Hai Bà Trưng': ['Bạch Mai', 'Đại Cồ Việt', 'Bà Triệu', 'Phố Huế', 'Lò Đúc'],
  'Đống Đa': ['Thái Hà', 'Láng', 'Khâm Thiên', 'Xã Đàn', 'Đặng Văn Ngữ'],
  'Cầu Giấy': ['Xuân Thủy', 'Yên Hòa', 'Quan Hoa', 'Hoàng Quốc Việt'],
  'Hoàng Mai': ['Trương Định', 'Lĩnh Nam', 'Định Công', 'Hoàng Mai'],
  'Long Biên': ['Nguyễn Văn Cừ', 'Ngọc Lâm', 'Cổ Linh', 'Bồ Đề'],
  'Hà Đông': ['Nguyễn Văn Lộc', 'Mỗ Lao', 'Văn Quán', 'Dương Nội'],
  'Nam Từ Liêm': ['Mỹ Đình', 'Lê Đức Thọ', 'Cầu Diễn', 'Xuân Phương'],
  'Bắc Từ Liêm': ['Phạm Văn Đồng', 'Cổ Nhuế', 'Kiều Mai', 'Phúc Diễn'],
  'Gia Lâm': ['Trâu Quỳ', 'Ngô Xuân Quảng', 'Đa Tốn', 'Cổ Bi'],
  'Hoài Đức': ['Trạm Trôi', 'An Khánh', 'Vân Canh', 'Song Phương'],
  'Thanh Trì': ['Ngọc Hồi', 'Tả Thanh Oai', 'Thanh Liệt', 'Tứ Hiệp'],
  'Thường Tín': ['Quốc lộ 1A', 'Phố Vồi', 'Nguyễn Phi Khanh'],
};

const STREET_HINTS = [
  ['đinh liệt', 'Đinh Liệt', 'Hoàn Kiếm'],
  ['lý quốc sư', 'Lý Quốc Sư', 'Hoàn Kiếm'],
  ['phủ doãn', 'Phủ Doãn', 'Hoàn Kiếm'],
  ['hàng tre', 'Hàng Tre', 'Hoàn Kiếm'],
  ['hàng bè', 'Hàng Bè', 'Hoàn Kiếm'],
  ['hàng buồm', 'Hàng Buồm', 'Hoàn Kiếm'],
  ['hàng đường', 'Hàng Đường', 'Hoàn Kiếm'],
  ['hàng lược', 'Hàng Lược', 'Hoàn Kiếm'],
  ['hàng phèn', 'Hàng Phèn', 'Hoàn Kiếm'],
  ['hàng giầy', 'Hàng Giầy', 'Hoàn Kiếm'],
  ['hàng bông', 'Hàng Bông', 'Hoàn Kiếm'],
  ['hàng chĩnh', 'Hàng Chĩnh', 'Hoàn Kiếm'],
  ['hàng mắm', 'Hàng Mắm', 'Hoàn Kiếm'],
  ['mã mây', 'Mã Mây', 'Hoàn Kiếm'],
  ['cầu gỗ', 'Cầu Gỗ', 'Hoàn Kiếm'],
  ['lê duẩn', 'Lê Duẩn', 'Đống Đa'],
  ['phan đình giót', 'Phan Đình Giót', 'Thanh Xuân'],
  ['trương định', 'Trương Định', 'Hoàng Mai'],
  ['đội cấn', 'Đội Cấn', 'Ba Đình'],
  ['quán thánh', 'Quán Thánh', 'Ba Đình'],
  ['phan đình phùng', 'Phan Đình Phùng', 'Ba Đình'],
  ['phạm văn đồng', 'Phạm Văn Đồng', 'Bắc Từ Liêm'],
  ['nguyễn văn lộc', 'Nguyễn Văn Lộc', 'Hà Đông'],
  ['mỗ lao', 'Mỗ Lao', 'Hà Đông'],
  ['văn quán', 'Văn Quán', 'Hà Đông'],
  ['nguyễn văn cừ', 'Nguyễn Văn Cừ', 'Long Biên'],
  ['ngọc lâm', 'Ngọc Lâm', 'Long Biên'],
  ['cổ linh', 'Cổ Linh', 'Long Biên'],
  ['bạch mai', 'Bạch Mai', 'Hai Bà Trưng'],
  ['võ thị sáu', 'Võ Thị Sáu', 'Hai Bà Trưng'],
  ['nguyễn đình chiểu', 'Nguyễn Đình Chiểu', 'Hai Bà Trưng'],
  ['đại cồ việt', 'Đại Cồ Việt', 'Hai Bà Trưng'],
  ['thái hà', 'Thái Hà', 'Đống Đa'],
  ['khâm thiên', 'Khâm Thiên', 'Đống Đa'],
  ['xã đàn', 'Xã Đàn', 'Đống Đa'],
  ['đặng văn ngữ', 'Đặng Văn Ngữ', 'Đống Đa'],
  ['xuân thủy', 'Xuân Thủy', 'Cầu Giấy'],
  ['yên hòa', 'Yên Hòa', 'Cầu Giấy'],
  ['hoàng quốc việt', 'Hoàng Quốc Việt', 'Cầu Giấy'],
  ['âu cơ', 'Âu Cơ', 'Tây Hồ'],
  ['thụy khuê', 'Thụy Khuê', 'Tây Hồ'],
  ['yên phụ', 'Yên Phụ', 'Tây Hồ'],
  ['nghi tàm', 'Nghi Tàm', 'Tây Hồ'],
  ['mỹ đình', 'Mỹ Đình', 'Nam Từ Liêm'],
  ['lê đức thọ', 'Lê Đức Thọ', 'Nam Từ Liêm'],
  ['trâu quỳ', 'Trâu Quỳ', 'Gia Lâm'],
  ['ngô xuân quảng', 'Ngô Xuân Quảng', 'Gia Lâm'],
  ['an khánh', 'An Khánh', 'Hoài Đức'],
  ['trạm trôi', 'Trạm Trôi', 'Hoài Đức'],
].sort((left, right) => right[0].length - left[0].length);

const ALL_DISTRICTS = Object.keys(DISTRICT_CENTROIDS);
const PLACEHOLDER_ADDRESS = /địa chỉ chi tiết không hiển thị trong listing/i;

const removeDiacritics = (value) => String(value || '')
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/đ/g, 'd')
  .replace(/Đ/g, 'D');

const normalized = (value) => removeDiacritics(value)
  .toLowerCase()
  .replace(/[^a-z0-9]+/g, ' ')
  .trim();

const hashNumber = (value) => Number.parseInt(
  createHash('sha1').update(String(value)).digest('hex').slice(0, 8),
  16,
);

const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const isPlaceholderAddress = (address) => !address || PLACEHOLDER_ADDRESS.test(String(address));

const districtFromRecord = (restaurant) => {
  const district = String(restaurant.district || '').trim();
  return ALL_DISTRICTS.includes(district) ? district : null;
};

const streetHintFromName = (name) => {
  const text = normalized(name);
  const numberedHints = [];
  for (const [alias, street, district] of STREET_HINTS) {
    const escapedAlias = escapeRegExp(normalized(alias));
    const beforeStreet = new RegExp(`(?:^|[\\s-])([0-9]{1,4}[a-z]?(?:/[0-9]{1,4})?)\\s+${escapedAlias}(?=$|[\\s-])`).exec(text);
    const afterStreet = new RegExp(`${escapedAlias}\\s+([0-9]{1,4}[a-z]?)`).exec(text);
    if (beforeStreet || afterStreet) {
      numberedHints.push({
        street,
        district,
        number: (beforeStreet?.[1] || afterStreet?.[1]).toUpperCase(),
        index: Math.max(beforeStreet?.index || -1, afterStreet?.index || -1),
      });
    }
  }
  if (numberedHints.length) return numberedHints.sort((left, right) => right.index - left.index)[0];
  for (const [alias, street, district] of STREET_HINTS) {
    if (text.includes(normalized(alias))) return { street, district, number: null };
  }
  return null;
};

const chooseDistrict = (restaurant, restaurantKey, streetHint) => {
  const currentDistrict = districtFromRecord(restaurant);
  if (currentDistrict) return currentDistrict;
  if (streetHint?.district && ALL_DISTRICTS.includes(streetHint.district)) return streetHint.district;
  return ALL_DISTRICTS[hashNumber(`district:${restaurantKey}`) % ALL_DISTRICTS.length];
};

const chooseStreet = (district, restaurantKey, streetHint) => {
  if (streetHint?.street) return streetHint.street;
  const streets = DISTRICT_STREETS[district] || DISTRICT_STREETS['Hoàn Kiếm'];
  return streets[hashNumber(`street:${restaurantKey}`) % streets.length];
};

const mockCoordinates = (restaurantKey, district) => {
  const [latitude, longitude] = DISTRICT_CENTROIDS[district] || DISTRICT_CENTROIDS['Hoàn Kiếm'];
  const seed = hashNumber(`coordinates:${restaurantKey}`);
  const latitudeOffset = ((seed % 1000) / 1000 - 0.5) * 0.004;
  const longitudeOffset = ((Math.floor(seed / 1000) % 1000) / 1000 - 0.5) * 0.004;
  return {
    addressLat: Number((latitude + latitudeOffset).toFixed(6)),
    addressLng: Number((longitude + longitudeOffset).toFixed(6)),
  };
};

const mockAddress = (restaurant, restaurantKey) => {
  const streetHint = streetHintFromName(restaurant.name);
  const district = chooseDistrict(restaurant, restaurantKey, streetHint);
  const street = chooseStreet(district, restaurantKey, streetHint);
  const number = streetHint?.number || String((hashNumber(`number:${restaurantKey}`) % 198) + 2);
  return {
    address: `${number} ${street}, ${district}, Hà Nội`,
    district,
    ...mockCoordinates(restaurantKey, district),
  };
};

const mockPhone = (restaurantKey, usedPhones) => {
  let suffix = hashNumber(`phone:${restaurantKey}`) % 100_000_000;
  let phone = `09${String(suffix).padStart(8, '0')}`;
  while (usedPhones.has(phone)) {
    suffix = (suffix + 1) % 100_000_000;
    phone = `09${String(suffix).padStart(8, '0')}`;
  }
  usedPhones.add(phone);
  return phone;
};

const clone = (value) => JSON.parse(JSON.stringify(value));

export const enrichCatalog = (inputCatalog) => {
  const catalog = clone(inputCatalog);
  const restaurants = Array.isArray(catalog.restaurants) ? catalog.restaurants : [];
  const usedPhones = new Set(
    restaurants
      .map((restaurant) => String(restaurant.phone || '').trim())
      .filter((phone) => phone),
  );

  catalog.restaurants = restaurants.map((restaurant) => {
    const restaurantKey = String(restaurant.restaurantKey || restaurant.name || 'restaurant');
    const provenance = { ...(restaurant.provenance || {}) };
    const sourceFacts = { ...(restaurant.sourceFacts || {}) };
    const generatedAddressAlready = provenance.address === 'synthetic_mock'
      && sourceFacts.addressConfidence === 'synthetic_mock'
      && !isPlaceholderAddress(restaurant.address);
    const needsAddress = isPlaceholderAddress(restaurant.address) && !generatedAddressAlready;
    const addressData = needsAddress ? mockAddress(restaurant, restaurantKey) : {};
    const existingPhone = String(restaurant.phone || '').trim();
    const nextRestaurant = {
      ...restaurant,
      ...(needsAddress ? addressData : {}),
      phone: existingPhone || mockPhone(restaurantKey, usedPhones),
      provenance,
      sourceFacts,
    };

    if (needsAddress) {
      provenance.address = 'synthetic_mock';
      provenance.coordinates = 'synthetic_mock';
      sourceFacts.addressConfidence = 'synthetic_mock';
      sourceFacts.addressGeneratedFrom = 'restaurant-name-and-district-hints';
      sourceFacts.coordinateConfidence = 'synthetic_mock-district-centroid-jitter';
    }
    if (!existingPhone) {
      provenance.phone = 'synthetic_mock';
      sourceFacts.phoneConfidence = 'synthetic_mock';
    }

    return nextRestaurant;
  });

  const syntheticFields = Array.isArray(catalog.provenancePolicy?.synthetic)
    ? [...catalog.provenancePolicy.synthetic]
    : [];
  for (const field of [
    'phone',
    'address when source does not expose exact location',
    'coordinates for generated mock addresses',
  ]) {
    if (!syntheticFields.includes(field)) syntheticFields.push(field);
  }
  catalog.provenancePolicy = {
    ...(catalog.provenancePolicy || {}),
    synthetic: syntheticFields,
  };
  catalog.contactEnrichment = {
    schemaVersion: 1,
    policy: 'deterministic_mock_contact_data',
    sourceBackedAddressesPreserved: true,
    generatedAddressCoordinates: 'district-centroid-jitter',
    phoneFormat: 'Vietnamese 10-digit mock number',
    generatedFrom: 'scripts/workspace/hanoi-contact-data.mjs',
  };
  return catalog;
};

export { isPlaceholderAddress, mockAddress, mockPhone };
