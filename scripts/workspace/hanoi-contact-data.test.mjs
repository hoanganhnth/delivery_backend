import test from 'node:test';
import assert from 'node:assert/strict';

import { enrichCatalog } from './hanoi-contact-data.mjs';

const catalogFixture = () => ({
  schemaVersion: 1,
  dataset: 'test-catalog',
  city: 'Hà Nội',
  provenancePolicy: {
    sourceBacked: ['restaurant name', 'publicly listed address or service area'],
    normalized: ['Vietnamese text'],
    synthetic: ['approximate coordinates', 'phone'],
    notCaptured: [],
  },
  restaurants: [
    {
      restaurantKey: 'grabfood-pho-ly-quoc-su',
      name: 'Phở Lý Quốc Sư - 25 Phủ Doãn',
      address: 'Khu vực Hoàn Kiếm, Hà Nội (địa chỉ chi tiết không hiển thị trong listing)',
      district: 'Hoàn Kiếm',
      addressLat: 21.0285,
      addressLng: 105.8542,
      phone: null,
      provenance: { address: 'synthetic_mock', coordinates: 'synthetic_mock' },
      sourceFacts: { addressConfidence: 'not-exposed-in-listing' },
    },
    {
      restaurantKey: 'shopeefood-bep-nha',
      name: 'Bếp Nhà',
      address: '12 Nguyễn Huệ, Quận 1, Thành phố Hồ Chí Minh',
      district: 'Ngoài Hà Nội',
      addressLat: 10.7769,
      addressLng: 106.7009,
      phone: '0901234567',
      provenance: { address: 'source_backed', coordinates: 'synthetic_mock' },
      sourceFacts: { addressConfidence: 'source-detail-or-listing' },
    },
  ],
  menuItems: [],
});

test('enriches incomplete contacts deterministically and keeps source-backed addresses', () => {
  const first = enrichCatalog(catalogFixture());
  const second = enrichCatalog(first);
  const [mocked, sourceBacked] = first.restaurants;

  assert.equal(mocked.address, '25 Phủ Doãn, Hoàn Kiếm, Hà Nội');
  assert.match(mocked.phone, /^09\d{8}$/);
  assert.equal(mocked.provenance.address, 'synthetic_mock');
  assert.equal(mocked.provenance.phone, 'synthetic_mock');
  assert.equal(mocked.provenance.coordinates, 'synthetic_mock');
  assert.equal(mocked.sourceFacts.addressConfidence, 'synthetic_mock');
  assert.equal(mocked.sourceFacts.phoneConfidence, 'synthetic_mock');
  assert.notEqual(mocked.addressLat, 21.0285);
  assert.notEqual(mocked.addressLng, 105.8542);

  assert.equal(sourceBacked.address, '12 Nguyễn Huệ, Quận 1, Thành phố Hồ Chí Minh');
  assert.equal(sourceBacked.phone, '0901234567');
  assert.equal(sourceBacked.provenance.address, 'source_backed');
  assert.equal(sourceBacked.provenance.phone, undefined);
  assert.equal(sourceBacked.addressLat, 10.7769);
  assert.equal(sourceBacked.addressLng, 106.7009);

  assert.deepEqual(second, first);
  assert.notEqual(mocked.phone, sourceBacked.phone);
});
