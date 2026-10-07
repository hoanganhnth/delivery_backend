import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';

const backend = new URL('../../../../', import.meta.url);
const contract = JSON.parse(fs.readFileSync(new URL('./http-contract.json', import.meta.url), 'utf8'));

// These real DTOs have simple, non-nested declarations. Independently enumerate
// them to catch fields lost to trailing/Javadoc comments, Lombok or initializers.
const dtoSources = [
  ['delivery', 'delivery_service', 'request', 'AcceptDeliveryRequest'],
  ['delivery', 'delivery_service', 'request', 'CreateProofUploadIntentRequest'],
  ['delivery', 'delivery_service', 'response', 'DeliveryResponse'],
  ['match', 'match_service', 'request', 'FindNearbyShippersRequest'],
  ['settlement', 'settlement_service', 'request', 'CreatePaymentRequest'],
  ['settlement', 'settlement_service', 'response', 'BalanceResponse'],
];

for (const [service, packageName, direction, name] of dtoSources) {
  test(`captures every source-declared field of ${name}`, () => {
    const source = fs.readFileSync(new URL(
      `${service}/infrastructure/src/main/java/com/delivery/${packageName}/dto/${direction}/${name}.java`,
      backend,
    ), 'utf8');
    const expected = [...source.matchAll(/^\s*private\s+([\w.<>?,\s\[\]]+?)\s+(\w+)\s*(?:=[^;]*)?;/gm)]
      .map(([, type, fieldName]) => ({ name: fieldName, type: type.replace(/\s+/g, ' ').trim() }));
    assert.ok(expected.length > 0, 'fixture must contain field declarations');
    const schema = contract.schemas[`com.delivery.${packageName}.dto.${direction}.${name}`];
    assert.ok(schema, 'DTO must be reachable from the HTTP contract');
    assert.deepEqual(schema.fields.map(({ name, type }) => ({ name, type })), expected);
  });
}

test('retains validation annotations after comments in AcceptDeliveryRequest', () => {
  const fields = contract.schemas['com.delivery.delivery_service.dto.request.AcceptDeliveryRequest'].fields;
  const field = (name) => fields.find((value) => value.name === name);
  assert.equal(field('orderId').required, true);
  assert.equal(field('action').required, true);
  assert.deepEqual(field('rejectReason').constraints, [{ name: 'Size', arguments: 'max = 500' }]);
  for (const [name, min, max] of [
    ['estimatedPickupTime', 'value = "0.0", inclusive = false', '"240.0"'],
    ['currentLat', '"8.0"', '"24.0"'],
    ['currentLng', '"102.0"', '"110.0"'],
  ]) {
    assert.equal(field(name).required, false);
    assert.deepEqual(field(name).constraints, [
      { name: 'DecimalMin', arguments: min },
      { name: 'DecimalMax', arguments: max },
    ]);
  }
});

test('reads the complete Pattern annotation containing parentheses in a string', () => {
  const schema = contract.schemas['com.delivery.delivery_service.dto.request.CreateProofUploadIntentRequest'];
  const field = schema.fields.find(({ name }) => name === 'contentType');
  assert.equal(field.required, true);
  assert.deepEqual(field.constraints, [
    { name: 'NotBlank', arguments: 'message = "contentType is required"' },
    { name: 'Pattern', arguments: 'regexp = "image/(jpeg|png|webp)", message = "contentType must be image/jpeg, image/png or image/webp"' },
  ]);
});

test('captures checkout fields and nested fields after parenthesized comments', () => {
  const name = 'com.delivery.order_service.dto.response.CheckoutPreviewResponse';
  const schema = contract.schemas[name];
  for (const field of ['shippingFee', 'discountAmount', 'totalPrice', 'couponMessage']) {
    assert.ok(schema.fields.some(({ name }) => name === field), field);
  }
  const expected = ['menuItemId', 'menuItemName', 'oldPrice', 'newPrice'];
  assert.deepEqual(schema.nestedTypes.PriceChangeInfo.fields.map(({ name }) => name), expected);
  assert.deepEqual(contract.schemas[`${name}.PriceChangeInfo`].fields.map(({ name }) => name), expected);
  assert.ok(contract.schemas['com.delivery.order_service.dto.response.OrderResponse']
    .fields.some(({ name }) => name === 'cancelReason'));
});

test('keeps controller type and parameter offsets aligned after emoji comments', () => {
  const operations = contract.operations.filter(({ controller }) => controller === 'PaymentController');
  for (const [handler, returnType, parameterName, parameterType] of [
    ['vnpayIpn', 'ResponseEntity<Map<String, String>>', 'params', 'Map<String, String>'],
    ['getPaymentStatus', 'ResponseEntity<BaseResponse<PaymentOrderResponse>>', 'paymentId', 'Long'],
    ['getPaymentByRef', 'ResponseEntity<BaseResponse<PaymentOrderResponse>>', 'paymentRef', 'String'],
    ['getAvailableProviders', 'ResponseEntity<BaseResponse<Set<String>>>'],
  ]) {
    const operation = operations.find((value) => value.handler === handler);
    assert.ok(operation, handler);
    assert.equal(operation.java.returnType, returnType);
    assert.deepEqual(operation.java.parameters.map(({ name, type }) => ({ name, type })),
      parameterName ? [{ name: parameterName, type: parameterType }] : []);
  }
});
