// React Hook Form + Yup validation.
import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { withAuth } from '@components/withAuth.jsx';
import { api } from '@services/apiService.js';

const schema = yup.object({
  tradeRef:       yup.string()
                     .matches(/^[A-Z]{3}-\d{8}-\d{4}$/, 'Format: AAA-YYYYMMDD-NNNN')
                     .required('Trade reference is required'),
  instrumentId:   yup.number().typeError('Must be a number').integer().positive().required(),
  counterpartyId: yup.number().typeError('Must be a number').integer().positive().required(),
  assetClass:     yup.string().oneOf(['EQUITY', 'FX', 'BOND', 'DERIVATIVE']).required(),
  side:           yup.string().oneOf(['BUY', 'SELL']).required(),
  quantity:       yup.number().typeError('Must be a number').positive().required(),
  price:          yup.number().typeError('Must be a number').positive().required(),
  tradeDate:      yup.date().typeError('Must be a valid date').required(),
});

function AddTrade() {
  const [serverError, setServerError] = useState(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isSubmitSuccessful },
    reset,
  } = useForm({
    resolver: yupResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      tradeRef: '',
      instrumentId: '',
      counterpartyId: '',
      assetClass: 'EQUITY',
      side: 'BUY',
      quantity: '',
      price: '',
      tradeDate: '',
    },
  });

  async function onSubmit(values) {
    setServerError(null);
    try {
      await api.createTrade(values);
      reset();
    } catch (err) {
      setServerError(err.message || 'Failed to create trade');
    }
  }

  return (
    <section>
      <h2>Add trade</h2>
      <form onSubmit={handleSubmit(onSubmit)} className="trade-form" noValidate>
        <label>Trade ref
          <input {...register('tradeRef')} placeholder="EQU-20260603-0001" />
        </label>
        {errors.tradeRef && <p role="alert" className="form-error">{errors.tradeRef.message}</p>}

        <label>Instrument id
          <input type="number" {...register('instrumentId')} />
        </label>
        {errors.instrumentId && <p role="alert" className="form-error">{errors.instrumentId.message}</p>}

        <label>Counterparty id
          <input type="number" {...register('counterpartyId')} />
        </label>
        {errors.counterpartyId && <p role="alert" className="form-error">{errors.counterpartyId.message}</p>}

        <label>Asset class
          <select {...register('assetClass')}>
            <option value="EQUITY">EQUITY</option>
            <option value="FX">FX</option>
            <option value="BOND">BOND</option>
            <option value="DERIVATIVE">DERIVATIVE</option>
          </select>
        </label>
        {errors.assetClass && <p role="alert" className="form-error">{errors.assetClass.message}</p>}

        <label>Side
          <select {...register('side')}>
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </label>
        {errors.side && <p role="alert" className="form-error">{errors.side.message}</p>}

        <label>Quantity
          <input type="number" step="0.0001" {...register('quantity')} />
        </label>
        {errors.quantity && <p role="alert" className="form-error">{errors.quantity.message}</p>}

        <label>Price
          <input type="number" step="0.0001" {...register('price')} />
        </label>
        {errors.price && <p role="alert" className="form-error">{errors.price.message}</p>}

        <label>Trade date
          <input type="date" {...register('tradeDate')} />
        </label>
        {errors.tradeDate && <p role="alert" className="form-error">{errors.tradeDate.message}</p>}

        {serverError && <p role="alert" className="form-error">{serverError}</p>}
        {isSubmitSuccessful && !serverError && <p role="status">Trade created.</p>}

        <button disabled={isSubmitting} type="submit">Submit</button>
      </form>
    </section>
  );
}

export default withAuth(AddTrade);
