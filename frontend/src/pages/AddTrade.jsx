// React Hook Form + Yup validation.
import React from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { withAuth } from '@components/withAuth.jsx';
import { api } from '@services/apiService.js';

const schema = yup.object({
  tradeRef:         yup.string().matches(/^[A-Z]{3}-\d{8}-\d{4}$/, 'AAA-YYYYMMDD-NNNN').required(),
  instrumentId:     yup.number().integer().positive().required(),
  counterpartyId:   yup.number().integer().positive().required(),
  assetClass:       yup.string().oneOf(['EQUITY','FX','BOND','DERIVATIVE']).required(),
  side:             yup.string().oneOf(['BUY','SELL']).required(),
  quantity:         yup.number().positive().required(),
  price:            yup.number().positive().required(),
  tradeDate:        yup.date().required(),
});

function AddTrade() {
  const { register, handleSubmit, formState: { errors, isSubmitting }, reset } =
        useForm({ resolver: yupResolver(schema) });

  async function onSubmit(values) {
    await api.createTrade(values);
    reset();
  }

  return (
    <section>
      <h2>Add trade</h2>
      <form onSubmit={handleSubmit(onSubmit)} className="trade-form">
        <label>Trade ref   <input {...register('tradeRef')} placeholder="EQU-20260603-0001" /></label>
        {errors.tradeRef && <p className="form-error">{errors.tradeRef.message}</p>}

        <label>Instrument id   <input type="number" {...register('instrumentId')} /></label>
        <label>Counterparty id <input type="number" {...register('counterpartyId')} /></label>
        <label>Asset class    <select {...register('assetClass')}>
          <option value="EQUITY">EQUITY</option><option value="FX">FX</option>
          <option value="BOND">BOND</option><option value="DERIVATIVE">DERIVATIVE</option>
        </select></label>
        <label>Side <select {...register('side')}>
          <option value="BUY">BUY</option><option value="SELL">SELL</option>
        </select></label>
        <label>Quantity  <input type="number" step="0.0001" {...register('quantity')} /></label>
        <label>Price     <input type="number" step="0.0001" {...register('price')} /></label>
        <label>Trade date<input type="date" {...register('tradeDate')} /></label>

        <button disabled={isSubmitting} type="submit">Submit</button>
      </form>
    </section>
  );
}

export default withAuth(AddTrade);